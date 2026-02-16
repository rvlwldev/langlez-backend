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

### 0. ABSOLUTE RULE: KOREAN ONLY 🇰🇷

- **All communication, explanations, and documentation MUST be in Korean.**
- **모든 대화, 설명, 문서는 반드시 한국어로 작성해야 합니다.**
- This is a strict requirement for all agents and sessions.

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
  - API DTOs MUST be named `*Request`, `*Response`. DO NOT use `*Dto`.
- **Command Objects**:
  - If a service method has **3 or fewer parameters**, pass them directly (no Command object).
  - If a service method has **4 or more parameters**, create a Command object.

  ```kotlin
  // ✅ 3개 이하: 직접 파라미터
  fun updateNickname(email: String, nickname: String): Member

  // ✅ 4개 이상: Command 객체 사용
  fun updateProfile(email: String, command: UpdateProfileCommand): Member
  ```

- **DTO Folder Structure**:
  - When a module has multiple DTOs, organize them in subdirectories:
    ```
    api/
    ├── request/
    │   └── UpdateProfileRequest.kt
    ├── response/
    │   └── MemberResponse.kt
    └── MemberController.kt
    application/
    ├── command/
    │   └── CreateMemberCommand.kt
    └── MemberService.kt
    ```
  - One data class per file (flat structure within folders).

- **Country/Language Codes**:
  - Use **ISO 3166-1 alpha-2** for countries: `KR`, `JP`, `US`
  - Use **ISO 639-1** for languages: `ko`, `ja`, `en`
  - Client shows localized names, server stores ISO codes only.

- **API Design - Availability Checks**:
  - For boolean availability checks (e.g., "is username available?"), use HTTP status codes instead of response body:
    - `200 OK` → Available / Success
    - `409 CONFLICT` → Already taken / Conflict
  - Return empty response body (or minimal info). Client interprets status code.

  ```kotlin
  // ✅ Simple: HTTP Status only
  @GetMapping("/check-account-name")
  fun checkAccountName(@RequestParam accountName: String) {
      if (!memberService.isAccountNameAvailable(accountName))
          throw ConflictException("Account name already taken")
  }
  ```

- **Logging**: Use `com.langlez.logger.PerformanceLogger` or `slf4j`.
- **Error Handling**:
  - Use `common:exception` for global error handling.
  - **Single Exception Strategy**: Use **ONLY** `LanglezException` for all business logic errors.
  - Do NOT create specific exception classes like `MemberNotFoundException`. Instead, use `LanglezException(HttpStatus.NOT_FOUND, "member.not-found")`.
  - **Exception Translation**: Catch infrastructure exceptions (e.g., `OptimisticLockingFailureException`) in the Service layer and rethrow as `LanglezException`.
  - **Domain Exceptions**: Use standard Java exceptions (`IllegalArgumentException`, `IllegalStateException`) for pure domain logic validation (inside Entities).
  - Any exception other than `LanglezException` is treated as an unexpected/unhandled error (`500 INTERNAL_SERVER_ERROR`).
- **Concurrency**: Always consider race conditions. Use Optimistic/Pessimistic Locking where appropriate.
- **Coroutines**: Use `suspend` functions and Coroutines ONLY when there is a clear performance benefit (e.g., non-blocking I/O with R2DBC, parallel external API calls). Do not force it on JDBC.
- **JPA Entity**:
  - ID field MUST be declared first with default value `0L`:

  ```kotlin
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0,
  ```

  - **Formatting**: Annotation and field must be on separate lines, with a blank line between fields:

  ```kotlin
  // ✅ Good
  @Column(nullable = false, unique = true)
  val email: String,

  @Column(name = "account_name", nullable = false, unique = true)
  var accountName: String,

  @Column(nullable = false)
  var nickname: String,
  ```

#### User's Kotlin Style (AI Agents MUST Follow)

사용자(Owner)의 코딩 스타일을 분석하여 정리한 가이드입니다. AI 에이전트는 이 스타일을 **반드시 모방**해야 합니다.

**1. 간결한 문법 선호 (Concise Syntax)**

- 불필요한 중괄호(`{}`)를 제거합니다.
- 단일 문장의 `if`, `for`는 한 줄로 작성합니다.

  ```kotlin
  // ✅ Good
  if (keys.isNotEmpty()) return keys.sortedBy { it.first }.map { it.second }

  for (i in args.indices) if (parameterAnnotations[i].any { it is LockKey })
      keys.add(parameterNames[i] to (args[i]?.toString() ?: "null"))

  // ❌ Bad (Verbose)
  if (keys.isNotEmpty()) {
      return keys.sortedBy { it.first }.map { it.second }
  }

  for (i in args.indices) {
      if (parameterAnnotations[i].any { it is LockKey }) {
          keys.add(parameterNames[i] to (args[i]?.toString() ?: "null"))
      }
  }
  ```

**2. 단일 표현식 함수 (Single-Expression Functions)**

- 함수 본문이 단일 표현식이면 `=`를 사용합니다.

  ```kotlin
  // ✅ Good
  private fun generateLockKey(prefix: String, keys: List<String>): String =
      "$prefix${keys.joinToString(":")}"

  fun isLocked(key: String): Boolean = redis.hasKey(key) ?: false
  ```

**3. `apply {}` 블록 적극 활용**

- 객체 초기화 시 `apply {}`로 프로퍼티를 설정합니다.

  ```kotlin
  // ✅ Good
  return RedisTemplate<String, Any>().apply {
      connectionFactory = factory
      keySerializer = StringRedisSerializer()
      valueSerializer = jsonSerializer
  }

  val standaloneConfig = RedisStandaloneConfiguration(properties.host, properties.port)
      .apply { database = properties.database }
  ```

**4. 짧은 파라미터명 (Short Parameter Names)**

- 맥락상 명확하면 짧은 이름을 사용합니다.

  ```kotlin
  // ✅ Good
  fun redisTemplate(factory: RedisConnectionFactory, mapper: ObjectMapper)
  fun lock(point: ProceedingJoinPoint, distributedLock: DistributedLock)

  // ❌ Bad (Too verbose)
  fun redisTemplate(redisConnectionFactory: RedisConnectionFactory, objectMapper: ObjectMapper)
  ```

**5. KDoc 주석 간소화**

- 한 줄로 작성 가능하면 한 줄로.
- `@param`, `@return` 등 태그는 생략합니다.

  ```kotlin
  // ✅ Good
  /** 메서드 파라미터에서 @LockKey 어노테이션이 붙은 값들을 추출 (파라미터 이름 순 정렬) */
  private fun extractLockKeys(joinPoint: ProceedingJoinPoint): List<String> { ... }

  // ❌ Bad (Too verbose)
  /**
   * 메서드 파라미터에서 @LockKey 어노테이션이 붙은 값들을 추출합니다.
   * 파라미터 이름 순으로 정렬됩니다.
   * @param joinPoint AOP JoinPoint
   * @return 추출된 락 키 목록
   */
  ```

**6. 빈 줄 최소화 (Minimal Blank Lines)**

- 논리적 그룹 사이에만 빈 줄을 넣습니다.
- 메서드 내부에서 불필요한 빈 줄을 넣지 않습니다.

  ```kotlin
  // ✅ Good
  val signature = joinPoint.signature as MethodSignature
  val method = signature.method
  val parameterAnnotations = method.parameterAnnotations
  val args = joinPoint.args
  val parameterNames = signature.parameterNames ?: Array(args.size) { "arg$it" }

  val keys = mutableListOf<Pair<String, String>>()
  for (i in args.indices) if (parameterAnnotations[i].any { it is LockKey })
      keys.add(parameterNames[i] to (args[i]?.toString() ?: "null"))
  ```

**7. Early Return 패턴**

- 조건을 만족하면 즉시 반환하여 중첩을 줄입니다.
  ```kotlin
  // ✅ Good
  if (keys.isNotEmpty()) return keys.sortedBy { it.first }.map { it.second }
  return listOf(method.declaringClass.name, method.name)
  ```

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
  - **E2E Style Guide**:
    - Use **BehaviorSpec** (Given-When-Then) for E2E tests.
    - For sequential flows (Stateful), use **nested** `When`/`Then` blocks to maintain context.
    - Example:
      ```kotlin
      Given("Setup") {
          When("Step 1") {
              Then("Verify 1") { ... }
              When("Step 2") { ... }
          }
      }
      ```

- **Log Files**: All test execution logs MUST be saved in the `logs/` directory (e.g., `logs/test_run_01.log`).

### 6. Git & Commit (Mandatory)

- **Commit Policy**: **Do NOT commit automatically.** Only commit when explicitly requested by the user. (사용자가 요청할 때만 커밋합니다).
- **Language**: **Commit messages MUST be written in Korean.** (커밋 메시지는 반드시 한국어로 작성해야 합니다).
- **Message Format**: `type: description`
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
