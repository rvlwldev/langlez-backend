# Langlez 백엔드 - 종합 코드 리뷰 & 개선 계획

> 작성일: 2026-03-19

---

## 1. 프로젝트 개요

**언어 학습 소셜 네트워킹 플랫폼**의 Spring Boot 3+ Kotlin 백엔드.
멀티모듈 모놀리스 구조로 마이크로서비스 전환을 염두에 두고 설계됨.

---

## 2. 아키텍처

### 디렉토리 구조

```
langlez-backend/
├── app/api                  # 앱 진입점 (Spring Boot main)
├── core/                    # OutBoxEventPublisher 인터페이스
├── common/
│   ├── exception/           # 커스텀 예외, 글로벌 핸들러
│   ├── jackson/             # JSON 직렬화 설정
│   ├── observability/       # 로깅, 메트릭, SQL/NoSQL 관찰
│   ├── security/            # JWT, OAuth2, 필터
│   └── web/                 # Swagger 설정, 글로벌 에러 핸들링
├── infra/
│   ├── files/               # 파일 스토리지 (S3, 로컬)
│   ├── kafka/               # Kafka 프로듀서/컨슈머
│   ├── mongo/               # MongoDB
│   ├── mysql/               # MySQL + JPA + QueryDSL
│   └── redis/               # Redis(Redisson), 분산락, 다중 캐시
└── module/
    ├── auth/                # OAuth2 인증 (Google, Apple)
    ├── member/              # 회원 관리 + 캐싱
    ├── outbox/              # 이벤트 아웃박스 패턴
    ├── profile/             # 프로필 + 프로필 이미지
    ├── profile_backup/      # 프로필 백업 모듈
    └── relationship/        # 팔로우 / 블록
```

### 기술 스택

| 영역 | 기술 |
|------|------|
| 프레임워크 | Spring Boot 3.x + Kotlin + Java 21 (Virtual Threads) |
| DB | MySQL (JPA + QueryDSL), MongoDB, Redis (Redisson) |
| 메시징 | Apache Kafka |
| 스토리지 | AWS S3 (prod) / 로컬 파일시스템 (local) |
| 인증 | JWT + OAuth2 (Google, Apple) |
| 관찰가능성 | Prometheus (Micrometer), P6Spy, Logstash |

### 핵심 설계 패턴

- **Outbox Pattern**: DB 트랜잭션 내 이벤트 저장 → 5초 폴링 → Kafka 발행 → 최대 3회 재시도 → 일별 아카이빙
- **다중 캐시 전략**: Redis(Redisson) 1차 + 로컬(Caffeine) fallback → Redis 장애 시 자동 강등
- **AOP 분산락**: `@DistributedLock` + SpEL 키 표현식
- **Layered Architecture**: `api → application → domain ← infrastructure` (모듈 내부)
- **Repository Pattern**: 도메인 인터페이스 + 인프라 구현체 분리

---

## 3. 보안 취약점

### 🔴 CRITICAL (즉시 수정)

#### [C-1] OAuth2 토큰을 URL 파라미터로 전달
- **위치**: `module/auth/.../OAuth2SuccessHandler.kt`
- **위험**: 토큰이 브라우저 히스토리·서버 액세스 로그·Referer 헤더에 노출
- **수정**: HttpOnly 쿠키로 전환
```kotlin
// 현재 (취약)
.queryParam("refreshToken", refreshToken)

// 수정
response.addCookie(Cookie("refreshToken", refreshToken).apply {
    isHttpOnly = true; secure = true; path = "/"
})
```

#### [C-2] Kafka 디시리얼라이저 전체 패키지 신뢰
- **위치**: `infra/kafka/.../KafkaConfiguration.kt`
- **코드**: `JsonDeserializer.TRUSTED_PACKAGES = "*"`
- **위험**: 임의 객체 역직렬화 공격 가능
- **수정**: `"com.langlez.*"` 로 화이트리스트

#### [C-3] 리프레시 토큰 로테이션(RTR) 미구현
- **위치**: `module/auth/.../AuthService.kt`
- **위험**: 토큰 탈취 시 무제한 재사용 가능
- **수정**: 리프레시 토큰 사용 시 신규 발급 + 기존 폐기

---

### 🟠 HIGH

#### [H-1] 로그아웃 / 토큰 폐기 로직 없음
- 로그아웃 시 Redis에서 리프레시 토큰 삭제 미구현
- 액세스 토큰 블랙리스트 Redis 구현 필요

#### [H-2] CORS 설정 미정의
- `WebSecurityConfiguration`에 명시적 CORS 없음
- CSRF 공격 노출 가능성

#### [H-3] Rate Limiting 없음
- `/api/v1/auth/refresh`, OAuth2 콜백 등 무제한 호출 가능
- Spring Cloud CircuitBreaker 또는 커스텀 Rate Limiter 추가 필요

---

### 🟡 MEDIUM

#### [M-1] RelationshipRepositoryImpl 미완성
- `TODO("Not yet implemented")` 다수 존재
- 런타임에 `NotImplementedError` 발생 위험

#### [M-2] Block 엔티티 컬럼명 오류
```kotlin
// Block 엔티티인데 컬럼명이 follower/followed로 잘못 명명
@Column(name = "follower_id")  // → blocker_id 여야 함
@Column(name = "followed_id")  // → blocked_id 여야 함
```

#### [M-3] 리스트 API 페이지네이션 없음
- 팔로워/팔로잉 전체 조회 → 대규모 데이터 시 메모리 고갈

#### [M-4] 입력값 Sanitize 미흡
- Profile bio, goal, want 필드(최대 1000자) XSS 잠재 위험

---

### 🟢 LOW

#### [L-1] 매직 넘버 하드코딩
- `20` (username 길이), `1000` (bio 길이), `64` (Redis 커넥션 풀) 등 → 상수 추출

#### [L-2] P6Spy 프로덕션 오버헤드
- SQL 프록시는 성능 부담 → prod 프로파일에서 비활성화 확인 필요

#### [L-3] 낙관적 락 충돌 핸들링 없음
- `Member`, `Profile`에 version 필드 있으나 `OptimisticLockingFailureException` 처리 미구현

---

## 4. 코드 품질 평가

### 잘 된 것

- 모듈 경계 명확, 의존성 방향 올바름
- Kotlin 관용 코드 스타일 일관성
- OutBox 패턴 수준 높음 (재시도, 아카이빙, 분산락)
- 다중 캐시 fallback 전략 (운영 복원력)
- Sealed class / enum으로 타입 안전성 확보
- Prometheus + P6Spy + MongoDB/Redis 쿼리 로거

### 개선 필요

- **Virtual Threads + Coroutines 혼용**: Java 21 virtual threads 활성화 상태에서 Kotlin suspend function 혼재 → coroutine 제거 필요 (TODO 에 언급)
- **SocialControllerV1 미완성**: 대부분 주석처리 + TODO 만 존재
- **응답 DTO 불일치**: 일부 컨트롤러에서 도메인 엔티티 직접 반환 위험

---

## 5. 미구현 기능 (TODO 기반)

| 기능 | 상태 | 우선순위 |
|------|------|----------|
| 리프레시 토큰 로테이션 (RTR) | 미구현 | CRITICAL |
| 로그아웃 + 토큰 블랙리스트 | 미구현 | HIGH |
| CORS 설정 | 미구현 | HIGH |
| Rate Limiting | 미구현 | HIGH |
| RelationshipRepository 완성 | 미완성 | HIGH |
| 다국어 오류 메시지 | 미구현 | MEDIUM |
| 프로필 캐시 설정 | 미구현 | MEDIUM |
| 온라인 상태 추적 | 미착수 | MEDIUM |
| 프로필 이미지 썸네일 | 미착수 | LOW |
| Coroutine 제거 | 미완료 | MEDIUM |
| 채팅 / 피드 / 매칭 / WebRTC / 구독 | 미착수 | FUTURE |

---

## 6. API 엔드포인트 현황

| 엔드포인트 | 메서드 | 인증 | 상태 |
|-----------|--------|------|------|
| `/api/v1/auth/refresh` | POST | 없음 | 구현됨 |
| `/api/v1/relationship/follow/@{id}` | POST | JWT | 구현됨 |
| `/api/v1/relationship/block/@{id}` | POST | JWT | 미완성 |
| `/oauth2/authorization/*` | - | OAuth2 | Spring Security 자동 |
| `/login/oauth2/code/*` | - | OAuth2 | Spring Security 자동 |
| `/swagger-ui/**` | GET | 공개 | local 환경만 |

---

## 7. 프로덕션 배포 전 필수 체크리스트

### 반드시 완료
- [ ] 토큰 전달 방식 URL → HttpOnly 쿠키 변경 (C-1)
- [ ] Kafka `TRUSTED_PACKAGES` 화이트리스트 설정 (C-2)
- [ ] 리프레시 토큰 로테이션(RTR) 구현 (C-3)
- [ ] 로그아웃 + 액세스 토큰 블랙리스트 구현 (H-1)
- [ ] CORS 명시적 설정 (H-2)
- [ ] Rate Limiting 추가 (H-3)
- [ ] RelationshipRepositoryImpl 완성 (M-1)

### 해야 할 것
- [ ] 모든 리스트 API 페이지네이션 적용 (M-3)
- [ ] Block 엔티티 컬럼명 수정 (M-2)
- [ ] Coroutines 제거 (Virtual Threads로 통일)
- [ ] 입력값 검증 / Sanitize 강화 (M-4)
- [ ] 낙관적 락 충돌 핸들링 추가 (L-3)

---

## 8. 총평

**아키텍처 설계**: ★★★★☆
Outbox Pattern, 분산락, 다중 캐시 fallback 등 운영 환경을 고려한 선택들이 보임. 모듈 구조와 레이어 분리도 명확.

**보안 성숙도**: ★★☆☆☆
토큰 URL 노출(C-1), 토큰 로테이션 미구현(C-3)은 프로덕션 수준에 미달. 출시 전 반드시 해결 필요.

**코드 완성도**: ★★★☆☆
다수의 TODO, 미완성 레포지토리, 주석처리된 컨트롤러가 런타임 오류 위험으로 남아있음.

**코드 품질**: ★★★★☆
Kotlin 관용 코드 스타일 일관적이고 타입 안전성 확보. 단, Coroutine + Virtual Threads 혼용 정리 필요.
