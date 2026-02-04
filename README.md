# Langlez Backend Server

Langlez(랭크즈)는 언어 교환(Language Exchange)을 핵심으로 하는 글로벌 소셜 네트워킹 서비스의 백엔드 서버입니다.
대규모 트래픽 확장에 유연하게 대응하기 위해 MSA(Microservices Architecture)로의 전환이 용이한 **Modular Monolith (Vertical Slice Architecture 지향)** 구조로 설계되었습니다.

---

## 1. 프로젝트 개요 (Overview)

### 1.1. 주요 서비스 기능
Langlez는 언어 교환을 목적으로 하는 글로벌 소셜 플랫폼입니다.

#### A. 인증 및 회원 (Auth & Member)
- **소셜 로그인**: Google, Apple OAuth 2.0 기반 인증 및 JWT 발급/재발급/검증.
- **회원 관리**: 
    - **등급 시스템**: 일반(MEMBER), 프리미엄(PREMIUM, 구독형), 관리자(ADMIN).
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
* **module/member**: 회원 정보, 등급 관리(Member/Premium/Admin), 언어 설정, 관심사.
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

### 3.3. 패키지 구조 (Layered Architecture)
각 모듈은 헥사고날 아키텍처 대신 **Layered Architecture**를 기반으로 다음과 같이 구성합니다.

* **api**: 외부 요청 처리 계층.
    * **api/http**: REST API 컨트롤러 (`@RestController`).
    * **api/event**: 이벤트 리스너 (Kafka, Spring Event).
* **application**: 비즈니스 로직 처리 계층 (Service).
    * Service 객체, DTO, UseCase 등이 위치합니다.
* **domain**: 핵심 도메인 계층.
    * **Entity**: JPA 엔티티 및 핵심 도메인 로직.
* **infrastructure**: 기술적 구현 계층.
    * **Persistence**: JPA Repository 인터페이스 및 구현체.
    * **External**: 외부 API 클라이언트, 인프라 설정(Config), 유틸리티 등.

### 3.5. 모듈 추가 절차 (Module Creation)
새로운 모듈 생성 시 반드시 다음 절차를 따릅니다.
1. `settings.gradle.kts`에 모듈 경로(`include("...")`)를 추가합니다.
2. `build.gradle.kts`를 생성하고 필요한 의존성을 설정합니다.
3. 다른 모듈에서 참조할 경우 `implementation(project(":..."))`로 의존성을 연결합니다.

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
* **라이브러리**: 테스트 프레임워크는 **Kotest**와 **MockK**만을 사용합니다. (JUnit5, Mockito 지양)
* **명명 규칙**: 
    * 테스트 함수명은 **한국어 문장**으로 작성하며, 백틱(\`\`)으로 감쌉니다.
    * `@DisplayName` 어노테이션은 사용하지 않습니다. 함수명 자체가 설명을 대신합니다.
    * 예시: `` fun `구글 로그인 시 신규 회원이면 자동 가입된다`() ``
* **가독성**: BDD 스타일(Given/When/Then) 또는 행위 중심의 서술형 명세를 지향합니다.

### 4.5. 환경 일관성 (Environment Consistency)
개발(local)과 운영(prod) 환경은 **단일 코드베이스**를 유지합니다.
* 환경별 동작 차이는 오직 `application-{profile}.yml` 설정과 Spring Profile(`@Profile`)을 통해서만 제어합니다.
* 코드 내부에서 `if (env == "prod")`와 같은 명시적 분기 처리를 지양하고, 인터페이스와 구현체 분리(Strategy Pattern)를 통해 해결합니다.

### 4.6. Kotlin 관용구 (Idioms)
* **Null Safety**: Java의 `Optional` 사용을 **엄격히 금지**하고, Kotlin의 Nullable Type(`?`)을 활용합니다.
* **JPA**: 
    - `findById` 대신 Spring Data Kotlin 확장 함수인 `findByIdOrNull`을 사용합니다.
    - Query Method 정의 시 반환 타입을 `T?`로 명시하여 Null Safety를 보장합니다.

### 4.7. 다국어 지원 (I18n)
* **지원 언어**: 한국어(KO), 영어(EN), 스페인어(ES), 프랑스어(FR), 일본어(JA), 중국어(ZH).
* **에러 메시지**: 클라이언트의 `Accept-Language` 헤더에 따라 다국어 에러 메시지를 반환합니다.

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
* **Commit Strategy**: **커밋은 사용자가 직접 수행합니다.** 에이전트는 명시적인 요청("커밋해줘" 등)이 없는 한 절대 자동으로 커밋을 생성하지 마십시오.
