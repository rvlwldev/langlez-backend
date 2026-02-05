# PROJECT KNOWLEDGE BASE

**Generated:** 2026-02-04
**Updated:** 2026-02-05
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

### 3. Build & Configuration (Critical)

- **Gradle Version Catalog**:
  - MUST use `gradle/libs.versions.toml` for ALL dependencies and plugins.
  - **NO hardcoded versions** or strings in `build.gradle.kts` files.
  - Use `implementation(libs.dependency.name)` and `alias(libs.plugins.name)`.
- **Root-Level Configuration**:
  - `bootJar`/`jar` settings are managed in the root `build.gradle.kts`. **DO NOT duplicate** these configs in modules.
  - Only `app:api` has `bootJar` enabled. All other modules use standard `jar`.
- **Module Dependencies**:
  - `app:api` should only import modules (`implementation(project(":..."))`).
  - Avoid direct external dependencies in `app:api` unless necessary (e.g., devtools). Rely on transitive dependencies from modules.
- **Properties**:
  - Property keys in `application.yml` MUST match code references exactly.
  - Example: If code uses `@Value("\${jwt.secret}")`, YAML must be `jwt: secret: ...`, NOT `spring: jwt: secret:`.

### 4. Coding Conventions

- **Kotlin-First**:
  - NO `Optional<T>`. Use Kotlin's Nullable Types (`T?`).
  - Use `findByIdOrNull` extension for JPA.
- **Naming**:
  - Controllers MUST be named `*Controller` (e.g., `MemberController`). DO NOT use `*Api`.
- **Logging**: Use `com.langlez.logger.PerformanceLogger` or `slf4j`.
- **Error Handling**: Use `common:exception` for global error handling.
- **Concurrency**: Always consider race conditions. Use Optimistic/Pessimistic Locking where appropriate.
- **Coroutines**: Use `suspend` functions and Coroutines ONLY when there is a clear performance benefit (e.g., non-blocking I/O with R2DBC, parallel external API calls). Do not force it on JDBC.

### 5. Testing Protocols

- **Framework**: **Kotest** + **MockK** + **RestAssured** + **Testcontainers**.
- **Strategy**:
  - **E2E Tests (Mandatory)**: Test API endpoints using `Testcontainers` (Real DB/Redis) + `RestAssured`. Name files `*E2E*Test.kt`.
  - **Integration Tests**: Test Service layer logic. Name files `*ServiceTest.kt`.
  - **Unit Tests**: Test pure Domain logic only.
  - **Controller Unit Tests (Forbidden)**: Do not write MockMVC tests with mocked services. Use E2E tests instead.
- **Naming**: Test function names must be **Korean sentences** enclosed in backticks.
  - Example: ``fun `구글 로그인 시 신규 회원이면 자동 가입된다`()``
- **Verification**: Tests must pass before any code submission.

### 6. Git & Commit (Mandatory)

- **Automatic Commit**: 모든 작업 단위(Logical Unit)가 완료될 때마다 반드시 커밋을 수행합니다.
- **Message Format**: `type: description` (Korean).
  - `feat`: 새로운 기능 추가.
  - `fix`: 버그 수정.
  - `chore`: 빌드, 패키지 매니저, 문서 수정 등 (소스 코드 변경 없음).
  - `refactor`: 리팩토링 (기능 변경 없음).
  - `test`: 테스트 코드 추가/수정.
  - `docs`: 문서 수정.
  - **Example**: `feat: 구글 로그인 기능 구현`, `fix: JWT 만료 시간 버그 수정`

### 7. Parallel Development (Git Worktree) - MANDATORY

- **Worktree Structure**: All development MUST use Git Worktrees to allow parallel execution by multiple Agents.
  ```
  langlez-backend/
  ├── main/           # Core Repository (Do not work directly here unless hotfix)
  ├── feature-auth/   # Dedicated worktree for Auth Module
  ├── feature-member/ # Dedicated worktree for Member Module
  └── ...
  ```
- **Agent Protocol**:
  1. **Identify Module**: Determine which module you are working on.
  2. **Create/Switch Worktree**:
     - If worktree exists: Use `workdir="/Users/hj/project/langlez/langlez-backend/feature-{module}"`.
     - If not: Create it from `main` using `git worktree add ../feature-{module} feature/{module}`.
  3. **Work Isolation**: NEVER modify files in `main` while working on a feature. Use the designated worktree.
  4. **Merge**: Push from feature branch and merge via PR (or local merge if instructed).

### 8. Active Worktrees

현재 생성된 워크트리 목록입니다. 각 모듈 작업 시 해당 경로를 참조하십시오.

- **Member Module**: `../langlez-member` (Branch: `feature/module-member`)
- **Auth Module**: `../langlez-auth` (Branch: `feature/module-auth`)

### 9. Module Responsibilities

- **app/api**: Only for aggregation, configuration (profiles), and connecting infrastructure. **DO NOT** place domain-specific E2E tests here.
- **module/\***: Each module MUST contain its own Domain, Application, API layers, and **Tests (including E2E)**.

## COMMANDS

```bash
./gradlew build -x test      # Fast build
./gradlew test               # Run all tests
./gradlew app:api:bootRun    # Run API server
```
