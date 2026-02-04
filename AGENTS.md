# PROJECT KNOWLEDGE BASE

**Generated:** 2026-02-04
**Stack:** Kotlin, Spring Boot 3, JPA, MySQL, Redis, MongoDB, Kafka

## OVERVIEW
Langlez Backend Server. Modular Monolith architecture using Vertical Slices.
Core domains: `member` (User profiles), `auth` (OAuth2/JWT), `matching`, `chat`.

## STRUCTURE
```
./
├── app/api/            # Application Entry Point (REST, Observability Bridge)
├── module/             # Business Domains (Layered Architecture)
│   ├── member/         # Member Domain
│   └── auth/           # Authentication Domain
├── common/             # Shared Kernel (Pure utils, Configs)
│   ├── i18n/           # MessageSource
│   ├── security/       # Security Config & Token
│   └── logger/         # P6Spy, Logging
└── infra/              # Technology Adapters (Spring Data, Clients)
```

## AI GUIDELINES (MANDATORY)
These rules are crucial for AI agents working on this project.

### 1. Language & Communication
- **Response Language**: All responses must be in **Korean**.
- **Tone**: Concise, professional, and direct.

### 2. Architecture & Design
- **Strict Modularity**: Maintain `Modular Monolith` with `Vertical Slice Architecture`.
- **No Cross-Module Joins**: Database joins between modules are **STRICTLY FORBIDDEN**. Use Service Interfaces or Kafka events.
- **Logic Placement**: Business logic belongs in `module/application` or `module/domain`. `app/api` is for aggregation only.
- **Environment Consistency**: `local` and `prod` must use the same codebase. Use `@Profile` or `Strategy Pattern` for differences (e.g., Local Storage vs S3).

### 3. Coding Conventions
- **Kotlin-First**:
    - NO `Optional<T>`. Use Kotlin's Nullable Types (`T?`).
    - Use `findByIdOrNull` extension for JPA.
- **Logging**: Use `com.langlez.logger.PerformanceLogger` or `slf4j`.
- **Error Handling**: Use `common:exception` for global error handling.

### 4. Testing Protocols
- **Framework**: **Kotest** + **MockK** ONLY. (JUnit5 and Mockito are discouraged).
- **Naming**: Test function names must be **Korean sentences** enclosed in backticks.
    - Example: `` fun `구글 로그인 시 신규 회원이면 자동 가입된다`() ``
- **No DisplayName**: Do not use `@DisplayName`. The function name is sufficient.
- **Verification**: Tests must pass before any code submission.

### 5. Git & Commit (Mandatory)
- **Automatic Commit**: 모든 작업 단위(Logical Unit)가 완료될 때마다 반드시 커밋을 수행합니다.
- **Message Format**: `type: description` (Korean).
    - `feat`: 새로운 기능 추가.
    - `fix`: 버그 수정.
    - `chore`: 빌드, 패키지 매니저, 문서 수정 등 (소스 코드 변경 없음).
    - `refactor`: 리팩토링 (기능 변경 없음).
    - `test`: 테스트 코드 추가/수정.
    - `docs`: 문서 수정.
    - **Example**: `feat: 구글 로그인 기능 구현`, `fix: JWT 만료 시간 버그 수정`

### 6. Parallel Development (Git Worktree)
- **Worktree Usage**: 병렬로 여러 작업을 수행해야 할 경우 `git worktree`를 적극 활용합니다.
    - 메인 브랜치 오염 방지 및 컨텍스트 스위칭 비용 최소화.
    - `git worktree add ../feature-branch feature/new-feature`

### 7. Active Worktrees
현재 생성된 워크트리 목록입니다. 각 모듈 작업 시 해당 경로를 참조하십시오.
*   **Member Module**: `../langlez-member` (Branch: `feature/module-member`)
*   **Auth Module**: `../langlez-auth` (Branch: `feature/module-auth`)

### 7. Module Responsibilities
- **app/api**: Only for aggregation, configuration (profiles), and connecting infrastructure. **DO NOT** place domain-specific E2E tests here.
- **module/***: Each module MUST contain its own Domain, Application, API layers, and **Tests (including E2E)**.

## COMMANDS
```bash
./gradlew build -x test      # Fast build
./gradlew test               # Run all tests
./gradlew app:api:bootRun    # Run API server
```
