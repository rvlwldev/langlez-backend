# Langlez Backend Plan

> 언어 학습 소셜 네트워킹 플랫폼의 백엔드 개발 계획.
> 코딩/테스트 컨벤션은 [`AGENTS.md`](AGENTS.md)를 **단일 진실 소스(SSOT)**로 한다.
> 서비스 전체 기획은 루트 [`../../PLAN.md`](../../PLAN.md), 앱 개발 계획은 [`../../flutter/PLAN.md`](../../flutter/PLAN.md) 참조.

---

## 1. 아키텍처 및 기술 스택 (Architecture & Tech Stack)

* **Pattern**: Modular Monolith + Vertical Slice Architecture (마이크로서비스 전환 염두)
* **Language / Framework**: Kotlin 2.x, Spring Boot 3.5+, Java 21 (Virtual Threads)
* **Database (Hybrid)**:
  * **MySQL 8.0**: 회원, 권한, 에코(Echo, 피드/댓글/해쉬태그), 구독/결제 (정형 데이터 및 트랜잭션)
  * **MongoDB**: 채팅 메시지, 보이스룸 메타데이터, 로그 (비정형/대량 데이터)
  * **Redis**: 실시간 매칭 큐, 유저 접속 상태(Presence), Rate Limiting, 번역 캐시, 에코 인기 해쉬태그 트렌딩
* **Infrastructure**:
  * **Media**: AWS S3 + CloudFront (CDN) + Lambda@Edge (Real-time Resizing)
  * **Messaging**: Redis Streams (`core.MessageQueue` 추상화, OutBox 패턴과 연동)
  * **Communication**: WebRTC (Signaling via WebSocket)
* **Client**: Flutter (별도 코드베이스)

## 2. 백엔드 모듈 정의 및 책임 (Module Definition)

| 모듈명 | 주요 기능 및 책임 |
| :--- | :--- |
| `module:member` | 프로필 관리, 회원 탈퇴(30일 보관 정책), 유저 검색 |
| `module:auth` | 구글 OAuth 2.0, JWT 발급 및 검증 |
| `module:echo` | Echo 피드 업로드(MySQL), 해쉬태그/트렌딩(Redis), 필터링, 신고/블라인드 로직 |
| `module:chat` | 1:1 채팅, MongoDB 메시지 저장, 파일 전송 처리(S3 연동) |
| `module:matching` | 게임형 실시간 매칭 큐(Redis ZSET), 조건 완화 알고리즘 |
| `module:voiceroom` | 보이스 스트리밍(WebRTC), 무료/유료 권한별 채팅 제한 |
| `module:billing` | 구독 관리, 광고 제거 상태 확인 서비스 |
| `module:notification`| FCM 기반 푸시 알림, 인앱 알림 |

## 3. 핵심 기능별 상세 메커니즘 (Key Mechanisms)

### 실시간 매칭 엔진 (Matchmaking Queue)
* **큐 방식**: 유저가 매칭 시작 시 **Redis Sorted Set**에 등록. `Score = [LanguageLevel_Weight] + [Interest_Weight] + [WaitTime_Reduction]`
* **조건 완화 (Relaxation)**: 매칭 대기 시간이 10초 경과할 때마다 Redis Score의 가중치를 낮추어 검색 범위를 자동으로 확장.
* **즉시 연결**: 매칭 성공 시 즉시 `module:chat`을 호출하여 채팅방 ID 생성 후 WebSocket을 통해 양쪽 유저를 해당 방으로 이동.

### 비용 최적화 번역 (Hybrid Translation)
* **1단계 (Client)**: Google ML Kit (On-device)로 최대한 기기에서 처리하여 무료 제공.
* **2단계 (Cache)**: Redis에 동일 문장 번역 결과가 있는지 확인 (API 호출 최소화).
* **3단계 (LLM API)**: Groq 또는 Together AI 등 초저가형 Llama 3 기반 API 사용 (문맥 중심, 비용 절감 우선).

### 미디어 파이프라인 (Cloud-Native Media)
* **S3 Presigned URL**: 서버 부하를 줄이기 위해 클라이언트가 S3에 직접 업로드.
* **Lambda@Edge**: 유저가 이미지 요청 시(`?w=1080`), 엣지 로케이션에서 즉시 리사이징하여 캐싱.
* **동영상 제한**: 30초/50MB 제한은 업로드 전 클라이언트 검증 및 업로드 후 Lambda 트리거를 통한 사후 검증 수행.

## 4. 기능별 백엔드 제약 (Rate Limit / Quota)

> 서비스 기능의 상세 기획은 루트 PLAN.md 참조. 아래는 백엔드에서 강제해야 하는 제약 요약.

| 기능 | 무료 회원 | 유료 회원 | 강제 위치 |
| :--- | :--- | :--- | :--- |
| 피드 작성 | 1회/일 (텍스트 500자, 사진 1장) | 무제한 (동영상 1분, 사진 10장) | Redis 카운터 |
| 댓글 | 10회/일 | 무제한 | Redis 카운터 |
| 채팅방 생성 | 5명/일 | 30명/일 | Redis 카운터 |
| 메시지 번역 | 10회/일 | 무제한 | Redis 카운터 |
| 음성통화 | 15분/일, 1회 5분 | (영상통화 무제한) | Redis + 세션 관리 |
| 추천 갱신 | 매일 자정 15명 | 매시간 갱신 | 스케줄러 / 캐시 TTL |
| 광고 노출 | 피드 5개당 1개, 추천 3명당 1개 | 완전 제거 | 응답 조립 시 role 분기 |

---

## 5. 백엔드 개발 로드맵

### Phase 1: 핵심 기반 구축 ✅
- [ ] 프로젝트 구조: Modular Monolith (app, module, common, infra, core)
- [ ] 인프라: Docker Compose (MySQL, MongoDB, Redis Sentinel, Kafka, Monitoring)
- [ ] 공통 모듈: Security, i18n, 예외 처리, 관찰가능성 (P6Spy, Prometheus)
- [ ] OutBox 패턴: 분산 이벤트 발행 (5초 폴링, Redisson 분산락, Kafka, 아카이빙)
- [ ] 캐시 계층: Redis(Redisson) + Caffeine 로컬 폴백 (ResilientCache)

### Phase 2: 인증 및 회원 ✅
- [ ] Auth: OAuth2 (Google) + JWT + Redis Refresh Token
- [ ] Member: 회원 CRUD, 캐싱, 이벤트 발행
- [ ] Profile: 프로필 + 이미지 관리, 방문자 수 HyperLogLog
- [ ] Relationship: Follow/Unfollow/Block/Unblock + 커서 기반 페이지네이션
- [ ] 테스트: Auth, Member, Relationship 단위 테스트 (Kotest)

### Phase 3: 기존 모듈 보강
- [ ] Auth: 로그아웃 (토큰 블랙리스트), RTR (Refresh Token Rotation)
- [ ] Auth: 코루틴 → Virtual Threads 전환 (FileStorage)
- [ ] Member: 온라인 상태 조회 (Redis Presence, 30분 TTL)
- [ ] Profile: 캐시 설정 강화, 이미지 썸네일 생성

### Phase 4: Echo(피드) & 채팅
- [ ] Echo: 피드 CRUD(최대 1,000자, 미디어 12개), 좋아요, 신고/블라인드, 팔로우 우선 노출+추천 무한스크롤
- [ ] Echo: 해쉬태그 파싱, Redis 기반 인기 해쉬태그 트렌딩(1일/7일/30일), 시간별 DB 집계, 31일 초과 데이터 정리
- [ ] Chat: WebSocket (STOMP) + MongoDB 메시지 저장, 채팅방 관리, 파일 전송(사진/동영상/오디오)
- [ ] Chat: 입력중 표시, 읽음 처리(마지막 읽은 시각 갱신 방식)

### Phase 5: 매칭 & 실시간
- [ ] Matching: Redis ZSET 기반 실시간 매칭 큐, 일별 추천 알고리즘
- [ ] Voiceroom: WebRTC 보이스 스트리밍, 무료/유료 권한별 채팅 제한

### Phase 6: 결제 & 알림
- [ ] Billing: 구독 관리 (IAP), 광고 제거 상태 확인
- [ ] Notification: FCM 푸시 알림, 인앱 알림

## 6. 운영 환경 준비

### A. 인증 키 발급
- [ ] Google OAuth: GCP 콘솔에서 Client ID & Secret 발급, 리다이렉트 URI 등록
- [ ] Apple Sign In: Apple Developer 콘솔에서 Service ID, Key ID, Private Key(.p8) 발급

### B. 클라우드 인프라 구성
- [ ] 서버 인스턴스 생성 및 SSH Key 설정
- [ ] AWS S3 버킷 생성 및 IAM Access Key/Secret 발급
- [ ] Firebase (FCM) 프로젝트 생성 및 Service Account JSON 다운로드

### C. 운영 환경 설정
- [ ] `application-production.yml`에 인프라 접속 정보 입력
- [ ] CI/CD 파이프라인에 Secret Key 등록 (JWT, OAuth, DB, Redis, Kafka)

---

## 7. 코딩 / 테스트 컨벤션

백엔드 코드 작성 시 준수해야 하는 모든 규칙(모듈 구조, 레이어 아키텍처, Entity/Repository/Controller 패턴, API 식별자 규칙, 설정 관리, 빌드 명령, 테스트 피라미드 및 예제)은 **[`AGENTS.md`](AGENTS.md)** 에 정의되어 있다.

> `AGENTS.md`는 AI 코딩 에이전트가 자동으로 읽는 표준 파일이므로 컨벤션의 단일 진실 소스(SSOT)로 유지한다. 본 PLAN.md는 "무엇을 만들지(계획)", AGENTS.md는 "어떻게 만들지(규칙)"를 담당한다.

주요 항목 요약:
- **레이어 아키텍처**: `api → application → domain ← infrastructure`
- **API 식별자**: 엔티티 ID(Long) 노출 금지, `username`(String) + `@{username}` 경로 사용
- **Controller 얇게 / Service 두껍게**, 검증 로직은 도메인 객체 내부에
- **설정 통합**: `app/api`에서 중앙 관리, 모듈별 `application.yml` 금지
- **테스트 피라미드**: Domain Unit 70% / Integration 20% / E2E 10% (Kotest + MockK + TestContainers)
- **No coroutines** (Virtual Threads 사용)

세부 규칙과 코드 예제는 반드시 [`AGENTS.md`](AGENTS.md)를 참조할 것.
