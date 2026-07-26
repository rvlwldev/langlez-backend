# 관심사(Interest) 모듈 설계

## 배경

매칭 품질을 높이기 위해 관심사를 정식 도메인으로 분리한다. 현재 `Profile.interests`는 자유문자열
`Set<String>`이라 "등산"/"하이킹"처럼 같은 개념이 다른 문자열로 갈라지고, echo/profile/matching이
서로 다른 관심사 개념(echo=해시태그, profile/matching=자유문자열)을 쓰고 있어 통합이 안 된다.

이 작업은 `profile`+`matching`에서 쓰는 관심사를 새 `module/interest`로 완전히 분리하는 것. echo의
해시태그(검색/트렌드 목적)는 이번 범위에서 제외 — 완전히 별개 개념으로 유지한다.

## 범위

- 새 모듈 `module/interest` 신설
- 관심사 검색 기능(자동완성용, 언어별)
- Admin 관심사 병합 메뉴
- `profile`/`matching`이 `Profile.interests` 대신 이 모듈을 쓰도록 교체

**범위 제외**: 의미 기반(임베딩) 자동 병합 후보 추천 — 지금은 수동 검색+병합만. 나중에 필요해지면
별도 스펙으로.

## 데이터 모델

```kotlin
@Entity
class Interest(
    val id: Long = 0,
    var ko: String?, var en: String?, var ja: String?,
    var zhTW: String?, var zhCN: String?, var de: String?,
    var vi: String?, var id_: String?, var fr: String?,
    var pt: String?, var es: String?, var ru: String?,
)

@Entity
class MemberInterest(
    val id: Long = 0,
    val memberId: Long,
    val interestId: Long,
    // UNIQUE(memberId, interestId)
)
```

언어 컬럼 12개는 이 프로젝트가 이미 지원하는 `messages_{locale}.properties` 12개(ko, en, ja, zh_TW,
zh_CN, de, vi, id, fr, pt, es, ru)와 정확히 대응한다. 언어별 별도 alias 테이블은 두지 않는다 — 같은
언어 내 동의어("등산"/"하이킹")가 나중에 또 나타나도 admin이 다시 병합하면 그만이라는 판단(YAGNI).
병합 자체가 상시적인 관리 작업이라 "한 번 병합한 걸 영구히 기억"할 필요는 없다.

## 핵심 흐름

### 1. 회원 관심사 검색 (자동완성)
`GET /api/v1/interests/search?q={term}` — 요청자의 locale에 해당하는 컬럼 하나만 FULLTEXT
(`MATCH(locale컬럼) AGAINST(term)`)로 검색. 언어 컬럼마다 독립된 단일 컬럼 FULLTEXT 인덱스를 둔다
(12개). 여러 언어를 한 번에 뒤섞어 검색하지 않는다 — 유저는 자기 언어만, admin도 검색 시 언어 하나를
선택해서 그 컬럼만 본다.

### 2. 회원 관심사 설정
`PUT /api/v1/interests/me` (문자열 리스트 바디, 요청자의 locale 기준) — 각 문자열마다:
1. 요청자 locale 컬럼에서 정확히 일치하는 Interest 탐색
2. 없으면 새 `Interest` 생성, 요청자 locale 컬럼만 채우고 나머지는 null
3. 이 ID들로 `MemberInterest`를 갱신 (기존 것과 diff해서 추가/삭제)

동시 요청으로 같은 신규 관심사가 두 번 생성 시도되는 경우 `DataIntegrityViolationException`을 잡아
재조회 후 사용한다(이번 세션에 이미 적용한 것과 같은 패턴).

### 3. matching에서의 사용
`MatchingService.attemptMatch`의 `candidateProfile.interests.intersect(myProfile.interests).size`를
interest 모듈에서 두 회원의 `MemberInterest.interestId` Set을 받아 교집합 계산하는 것으로 교체한다.
언어 컬럼과는 무관 — 같은 `interestId`면 어느 언어로 골랐든 매칭된다.

### 4. Admin 관심사 병합
`POST /admin/interests/merge` (body: `{fromId, toId}`):
1. **백필**: `to`의 각 언어 컬럼이 null이고 `from`에 값이 있으면 그 값을 `to`로 복사
2. `MemberInterest`에서 `interestId=from`인 행을 전부 `to`로 UPDATE (단, 이미 `to`도 갖고 있는
   회원은 중복 방지 — 있으면 스킵, 없으면 갱신)
3. `Interest(id=from)` 삭제

병합 후에는 `from`이 완전히 사라지고 `to`만 남으며, `to`의 언어 컬럼은 두 관심사의 값을 합친 상태가
된다.

### 5. 기동 시 초기화
- 시드 러너: 기본 관심사 목록(여행, 영화, 음악, 운동 등)을 12개 언어 번역까지 포함해 멱등하게 삽입
  (canonicalName 격인 게 없으므로 `en` 컬럼 값 기준으로 존재 여부 체크)
- FULLTEXT 인덱스 생성 러너: 언어 컬럼 12개 각각에 대해 단일 컬럼 FULLTEXT 인덱스가 없으면
  `ALTER TABLE ... ADD FULLTEXT INDEX ...` native DDL 실행 (이 프로젝트는 Flyway/Liquibase 없이
  `ddl-auto=update`만 쓰므로, JPA 어노테이션만으로는 FULLTEXT를 선언할 수 없어 기동 시 직접 확인 후
  생성하는 방식을 택함)

## 에러 처리

- 회원 관심사 설정 중 동시 신규 생성 충돌: `DataIntegrityViolationException` catch 후 재조회
- 병합 시 `fromId == toId` → 400
- 병합 시 존재하지 않는 ID → 404

## 테스트

- `InterestService`: locale별 검색/신규생성/기존매칭 각 경로 단위테스트
- `InterestMergeService`: 병합 후 언어 컬럼 백필 정확성 + `MemberInterest` 중복 없이 리포인트되는지
  단위테스트, 실제 MySQL 통합테스트 1개
- `MatchingService`: interest 데이터 소스 교체 후 기존 매칭 로직 테스트 유지(mock 교체)
- FULLTEXT 검색: 실제 MySQL 붙는 통합테스트로 실제 인덱스 생성 + 검색 동작 확인

## 마이그레이션 메모

개발 중인 프로젝트라 기존 `Profile.interests`(자유문자열) 데이터는 신경 쓰지 않는다
(`ddl-auto=update`로 스키마만 갈아치우고 개발 DB는 재생성/재시드).
