# Langlez Backend Server

Langlez(랭크즈)는 언어 교환(Language Exchange)을 핵심으로 하는 글로벌 소셜 네트워킹 서비스의 백엔드 서버입니다.
대규모 트래픽 확장에 유연하게 대응하기 위해 MSA(Microservices Architecture)로의 전환이 용이한 **Modular Monolith (Vertical Slice Architecture 지향)** 구조로 설계되었습니다.

---

## 1. 프로젝트 개요 (Overview)

### 1.1. 주요 서비스 기능
Langlez는 언어 교환을 목적으로 하는 글로벌 소셜 플랫폼입니다.

#### A. 인증 및 회원 (Auth & Account)
- **소셜 로그인**: Google, Apple OAuth 2.0 기반 인증 및 JWT 발급/재발급/검증.
- **프로필 관리**: 닉네임, 사진(S3), 학습 언어(Level 1~5), 구사 언어(Native/Fluent), 관심사 태그, 차단 목록 관리.

#### B. 매칭 시스템 (Matching)
- **일일 유저 추천 (Daily Recommendation)**: 
    - 매일 자정 알고리즘이 선별한 파트너 리스트 제공 (학습/구사 언어, 관심사, 최근 접속일 기반).
    - 배치(Batch) 작업으로 생성하여 Redis 캐싱.
- **실시간 랜덤 매칭 (Real-time Random Matching)**: 
    - 선호 언어, 성별 필터를 설정하여 접속 중인 유저와 즉시 연결.
    - Redis Pub/Sub 또는 ZSet을 활용한 고성능 대기열 관리.

#### C. 커뮤니케이션 (Communication)
- **채팅 (Chat)**: 
    - 1:1 및 그룹 채팅, 미디어 전송, 읽음 처리.
    - 데이터 수명 주기 관리 (Active: 0~3개월 MongoDB, Archived: 3~6개월 S3 이관, Expired: 6개월+ 삭제).
- **라이브 룸 (Room)**: 
    - 다자간 음성/영상 통화 (WebRTC). 방장 권한(강퇴, 마이크 제어) 관리.

#### D. 커뮤니티 및 알림 (Feed & Notification)
- **피드 (Feed)**: 
    - X(트위터) 스타일의 단문+이미지 중심 피드. 팔로워 타임라인 Push Model(Redis List) 적용.
- **푸시 알림**: FCM/APNS 연동을 통한 실시간 알림 발송 및 알림 보관함 관리.

---

## 2. 기술 스택 (Tech Stack)

* **Language**: Kotlin 2.2+ (JDK 21 LTS)
* **Framework**: Spring Boot 3.5.8
* **Build Tool**: Gradle (Kotlin DSL, Version Catalog 적용)
* **Database**: 
    - MySQL (Relational Data)
    - MongoDB (Document Data)
    - Redis (Caching, Session, Queues)
* **Messaging**: Apache Kafka, WebSocket (STOMP)
* **Storage**: AWS S3 / Local Storage (환경별 추상화)
* **Observability**:
    - Logback (JSON Structured Logging)
    - Prometheus / Grafana (Metrics)
    - P6Spy (SQL Monitoring)

---

## 3. 아키텍처 및 프로젝트 구조 (Architecture)

### 3.1. 아키텍처 전략
* **Modular Monolith (Vertical Slice Architecture)**:
    - 각 모듈은 API, 비즈니스 로직, 데이터 접근 계층을 모두 포함하는 수직적 구조입니다.
    - 모듈 간 DB 조인은 금지되며 Service Interface 또는 Kafka 이벤트를 통해서만 통신합니다.
* **Logic Decoupling**: 
    - 비즈니스 로직은 각 도메인 모듈 내부에 존재해야 하며, 외부(app/api 등)로 유출되지 않아야 합니다.
* **Entry Point**: 
    - `app/api`는 각 도메인 모듈을 조합(Aggregate)하여 서비스를 제공하는 엔트리 포인트이자 게이트웨이 역할을 수행합니다.

### 3.2. 모듈 구조 (Module Structure)

#### 애플리케이션 실행 단위 (App)
* **app/api**: 클라이언트 요청을 받는 메인 API. 각 도메인 모듈을 조합하여 구동합니다.
* **app/admin**: 관리자용 백오피스 API 서버.
* **app/signaling**: WebRTC 및 실시간 통신을 위한 전용 소켓 서버.

#### 비즈니스 도메인 단위 (Module)
* **module/auth**: 인증, 소셜 로그인, 보안 정책.
* **module/account**: 유저 프로필, 언어 설정, 관심사.
* **module/matching**: 유저 추천 알고리즘 및 랜덤 매칭 대기열.
* **module/chat**: 메시지 처리, 미디어 전송, MongoDB 저장 및 수명 주기 관리.
* **module/feed**: SNS 피드, 좋아요/댓글, 팔로우 시스템.
* **module/room**: 음성/영상 라이브 룸 관리.
* **module/payment**: 인앱 결제 검증 및 멤버십 관리.
* **module/notification**: 푸시 알림 발송 및 보관함.

#### 인프라 및 공통 (Infra & Common)
* **infra/***: MySQL, MongoDB, Redis, Kafka, Files(S3) 설정.
* **common/exception**: 전역 예외 처리, 표준 에러 응답.
* **common/observability**: 로깅, 모니터링, 트레이싱.

---

## 4. 컨벤션 및 환경 설정 (Conventions)

### 4.1. Application Profiles
* **local** (Default): 로컬 개발 환경. Local Storage 사용.
* **prod**: 운영 환경. AWS S3 사용.

### 4.2. 코드 품질 (Code Quality)
이 프로젝트는 **Kotlin 공식 코딩 컨벤션**을 따르며, **Ktlint**를 통해 스타일을 강제합니다.
* **Lint 체크**: `./gradlew ktlintCheck`
* **자동 포맷팅**: `./gradlew ktlintFormat`

### 4.3. 의존성 관리 (Dependency Management)
모든 의존성은 Gradle **Version Catalog** (`gradle/libs.versions.toml`)를 통해 중앙 집중식으로 관리됩니다.

### 4.4. 테스트 (Testing)
* **명명 규칙**: 모든 테스트의 표시 이름(`@DisplayName`)은 **한국어**로 작성합니다.
* **가독성**: 비즈니스 시나리오를 설명하는 문장을 선호합니다.

---

## 5. 빌드 및 실행 (Build & Run)

### Build Project
```bash
./gradlew build
```

### Run Application (Local)
```bash
./gradlew :app:api:bootRun
```

---

## 6. AI 에이전트 지침 (AI Instructions)

이 문서는 AI 에이전트가 프로젝트를 이해하는 데 핵심적인 자료입니다. 작업 수행 시 아래 지침을 따르십시오.

* **Language**: 모든 응답은 반드시 **한국어**로 작성하십시오.
* **Architecture**: Modular Monolith 및 Vertical Slice Architecture 구조를 엄격히 준수하십시오.
* **Storage Strategy**: 파일 저장소 구현 시 환경별(Local/S3) 자동 전환 전략을 유지하십시오.
* **Refactoring**: 리팩토링 시 기존 테스트가 통과하는지 반드시 확인하십시오.
