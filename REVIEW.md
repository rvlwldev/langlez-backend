# Langlez Backend 코드 리뷰 리포트

**생성일:** 2026-02-05  
**분석 대상:** langlez-backend/main

---

## 1. 개발 진행도 요약

### Phase 1: Core Foundation ✅ (완료)

- [x] Modular Monolith 프로젝트 구조 (app, module, common, infra)
- [x] MySQL, Redis, MongoDB, Kafka 인프라 설정
- [x] 공통 모듈: Security, Logger, I18n, Exception Handling
- [x] Observability: P6Spy, Request Logging

### Phase 2: Auth & Member 🔄 (진행 중 - 약 85% 완료)

- [x] OAuth2 (Google/Apple) Spring Security 통합
- [x] Member 엔티티 설계, Repository (DIP 적용)
- [x] Auth 서비스 및 핸들러 유닛 테스트
- [x] Member API: 프로필 조회/수정 엔드포인트
- [x] JWT Refresh Token 로직 (Redis 미적용)
- [ ] ⚠️ Token Blacklist (로그아웃 시 토큰 무효화)
- [ ] ⚠️ 유효성 검증 로직 미비

### Phase 3, 4: Matching, Chat, Feed, Notification ❌ (미시작)

---

## 2. 코드 리뷰: 잠재적 위험 및 버그 가능성

### 🔴 Critical Issues (즉시 해결 필요)

#### 2.1 JWT 토큰 역할(Role) 하드코딩 문제

**파일:** [JwtAuthenticationFilter.kt](file:///Users/hj/project/langlez/langlez-backend/main/common/security/src/main/kotlin/com/langlez/security/filter/JwtAuthenticationFilter.kt#L27)

```kotlin
// 문제: 역할이 하드코딩됨 - PREMIUM 사용자도 MEMBER로 처리됨
val authorities = listOf(SimpleGrantedAuthority("ROLE_MEMBER"))
```

**위험:** Member 엔티티의 `role` 필드가 있음에도 JWT 필터에서 항상 `ROLE_MEMBER`로 설정됨. 향후 PREMIUM 권한 기반 기능 구현 시 문제 발생.

**권장 수정:**

```diff
- val authorities = listOf(SimpleGrantedAuthority("ROLE_MEMBER"))
+ // JWT 클레임에서 role 추출
+ val role = jwtTokenProvider.getRole(token) // 구현 필요
+ val authorities = listOf(SimpleGrantedAuthority(role))
```

---

#### 2.2 OAuth2SuccessHandler 역할 하드코딩

**파일:** [OAuth2SuccessHandler.kt](file:///Users/hj/project/langlez/langlez-backend/main/common/security/src/main/kotlin/com/langlez/security/config/OAuth2SuccessHandler.kt#L28)

```kotlin
val role = "ROLE_MEMBER"  // 하드코딩됨
```

**위험:** 새 사용자와 기존 사용자 모두 동일하게 `ROLE_MEMBER` 토큰 발급. DB에 저장된 실제 역할을 반영하지 않음.

---

#### 2.3 AuthService Refresh Token에서 역할 하드코딩

**파일:** [AuthService.kt](file:///Users/hj/project/langlez/langlez-backend/main/module/auth/src/main/kotlin/com/langlez/auth/application/AuthService.kt#L18)

```kotlin
val newAccessToken = tokenProvider.createAccessToken(email, "ROLE_MEMBER")
```

**위험:** 토큰 갱신 시에도 `ROLE_MEMBER` 하드코딩. PREMIUM으로 업그레이드해도 토큰 갱신 후 권한이 MEMBER로 reset됨.

---

#### 2.4 MemberResponse에서 NPE 가능성

**파일:** [MemberDto.kt](file:///Users/hj/project/langlez/langlez-backend/main/module/member/src/main/kotlin/com/langlez/member/api/MemberDto.kt#L28)

```kotlin
id = member.id!!,  // !! 연산자 - NPE 가능
```

**위험:** 새 Member 엔티티가 저장되기 전에 `from()`이 호출되면 `NullPointerException` 발생.

---

### 🟡 High Priority Issues (가까운 시일 내 해결 권장)

#### 2.5 입력 유효성 검증 부재

**파일:** [MemberController.kt](file:///Users/hj/project/langlez/langlez-backend/main/module/member/src/main/kotlin/com/langlez/member/api/MemberController.kt), [AuthController.kt](file:///Users/hj/project/langlez/langlez-backend/main/module/auth/src/main/kotlin/com/langlez/auth/api/AuthController.kt)

**문제:**

- `@Valid` 어노테이션 미사용
- `@NotBlank`, `@Size`, `@Email` 등 Bean Validation 미적용
- 빈 닉네임, 잘못된 이메일 형식 등 허용됨

**권장:**

```kotlin
data class UpdateMemberRequest(
    @field:NotBlank @field:Size(min = 2, max = 20)
    val nickname: String,
    // ...
)
```

---

#### 2.6 Refresh Token Redis 저장 미구현

**현재 상태:** Refresh Token이 Redis에 저장되지 않음. 토큰 무효화(로그아웃, 보안 침해) 불가능.

**권장:**

- Refresh Token을 Redis에 저장 (TTL: refresh-token-validity-in-seconds)
- AuthService.refresh()에서 Redis 저장값과 비교 검증
- 로그아웃 시 Redis에서 삭제

---

#### 2.7 OAuth2AuthenticationSuccessHandlerTest 실패 예상

**파일:** [OAuth2AuthenticationSuccessHandlerTest.kt](file:///Users/hj/project/langlez/langlez-backend/main/common/security/src/test/kotlin/com/langlez/security/config/OAuth2AuthenticationSuccessHandlerTest.kt)

**문제:**

- 테스트가 `createToken()` 메서드를 mock하지만, 실제 `JwtTokenProvider`에는 해당 메서드 없음
- 실제로는 `createAccessToken()`, `createRefreshToken()` 두 개가 존재
- 리다이렉트 URL 파라미터도 `token=` 대신 `accessToken=`, `refreshToken=` 두 개임

```kotlin
// 테스트 (잘못됨)
every { jwtTokenProvider.createToken("test@example.com") } returns "mock-jwt-token"
urlSlot.captured shouldContain "token=mock-jwt-token"

// 실제 코드
val accessToken = jwtTokenProvider.createAccessToken(email, role)
val refreshToken = jwtTokenProvider.createRefreshToken(email)
.queryParam("accessToken", accessToken)
.queryParam("refreshToken", refreshToken)
```

---

#### 2.8 테스트 비활성화 상태

**파일:**

- [DistributedLockTest.kt](file:///Users/hj/project/langlez/langlez-backend/main/infra/redis/src/test/kotlin/com/langlez/redis/distributedLock/DistributedLockTest.kt#L32)
- [ResilientCacheTest.kt](file:///Users/hj/project/langlez/langlez-backend/main/infra/redis/src/test/kotlin/com/langlez/redis/cache/ResilientCacheTest.kt#L44)

```kotlin
@org.junit.jupiter.api.Disabled("Temporary disabled due to configuration context issues")
```

**문제:** Sentinel 설정 강제로 인해 standalone Redis Testcontainer와 충돌. 테스트 실행 불가.

---

### 🟢 Medium Priority Issues (개선 권장)

#### 2.9 UpdateMemberCommand에서 API DTO 의존성

**파일:** [MemberUseCase.kt](file:///Users/hj/project/langlez/langlez-backend/main/module/member/src/main/kotlin/com/langlez/member/application/MemberUseCase.kt#L23)

```kotlin
val targetLanguages: List<com.langlez.member.api.TargetLanguageDto>?
```

**문제:** Application 레이어 Command가 API 레이어 DTO를 직접 참조 → 레이어 간 의존성 역전.

**권장:** Domain 레이어 `TargetLanguage`를 사용하고 Controller에서 변환.

---

#### 2.10 GlobalRestControllerAdvice 중복 파일

**발견:**

- [exception/src/main/kotlin/com/langlez/common/GlobalRestControllerAdvice.kt](file:///Users/hj/project/langlez/langlez-backend/main/common/exception/src/main/kotlin/com/langlez/common/GlobalRestControllerAdvice.kt)
- [exception/src/main/kotlin/com/langlez/common/exception/GlobalRestControllerAdvice.kt](file:///Users/hj/project/langlez/langlez-backend/main/common/exception/src/main/kotlin/com/langlez/common/exception/GlobalRestControllerAdvice.kt)

**위험:** 두 클래스 모두 `@RestControllerAdvice` → Bean 충돌, 예측 불가능한 예외 처리.

---

#### 2.11 Transaction Scope 불일치

**파일:** [MemberService.kt](file:///Users/hj/project/langlez/langlez-backend/main/module/member/src/main/kotlin/com/langlez/member/application/MemberService.kt)

```kotlin
@Transactional(readOnly = true)  // 클래스 레벨
fun getMember(...) { ... }  // OK

fun updateMember(...) { ... }  // readOnly = true 상속 → write 불가?
```

> 실제로는 클래스 레벨에 `@Transactional` (readOnly=false)이 있어 문제없음. 하지만 명시적 어노테이션 권장.

---

## 3. 누락된 테스트 시나리오

### Auth 모듈

| 시나리오                                         | 상태    | 우선순위 |
| ------------------------------------------------ | ------- | -------- |
| 만료된 Refresh Token으로 갱신 시도               | ❌ 누락 | Critical |
| 잘못된 형식의 토큰으로 갱신 시도                 | ❌ 누락 | Critical |
| Access Token으로 Refresh 시도 (잘못된 토큰 타입) | ❌ 누락 | High     |
| OAuth2 로그인 성공 시 토큰 발급 E2E              | ❌ 누락 | High     |
| 신규 사용자 OAuth 로그인 시 Member 자동 생성     | ❌ 누락 | High     |

### Member 모듈

| 시나리오                            | 상태    | 우선순위 |
| ----------------------------------- | ------- | -------- |
| GET /api/members/me E2E 테스트      | ❌ 누락 | Critical |
| PUT /api/members/me E2E 테스트      | ❌ 누락 | Critical |
| 존재하지 않는 회원 조회 시 404 반환 | ❌ 누락 | High     |
| 빈 닉네임으로 업데이트 시도         | ❌ 누락 | High     |
| 인증 없이 API 접근 시 401 반환      | ❌ 누락 | Critical |
| updateMember 서비스 테스트          | ❌ 누락 | Medium   |

### Security 모듈

| 시나리오                                 | 상태    | 우선순위 |
| ---------------------------------------- | ------- | -------- |
| JwtAuthenticationFilter 유효 토큰 테스트 | ❌ 누락 | High     |
| JwtAuthenticationFilter 만료 토큰 테스트 | ❌ 누락 | High     |
| JwtAuthenticationFilter 토큰 없음 테스트 | ❌ 누락 | Medium   |

### 현재 테스트 현황

```
활성화된 테스트: 6개
- AuthE2ETest: 1 테스트
- MemberE2ETest: 1 테스트
- MemberServiceTest: 2 테스트
- OAuth2AuthenticationSuccessHandlerTest: 1 테스트 (실패 예상)

비활성화된 테스트: 2개
- DistributedLockTest
- ResilientCacheTest
```

---

## 4. 아키텍처 준수 여부

| 규칙                             | 상태           | 비고                           |
| -------------------------------- | -------------- | ------------------------------ |
| No Cross-Module Joins            | ✅ 준수        |                                |
| Controller 네이밍 (\*Controller) | ✅ 준수        |                                |
| Optional 대신 Nullable 사용      | ✅ 준수        |                                |
| Kotest + MockK 사용              | ✅ 준수        |                                |
| 한국어 테스트 함수명             | ⚠️ 일부 미준수 | AuthE2ETest, MemberE2ETest     |
| E2E 테스트는 Module에 위치       | ✅ 준수        |                                |
| Application/Domain 레이어 분리   | ⚠️ 일부 위반   | UpdateMemberCommand → DTO 의존 |

---

## 5. 권장 우선순위 액션 플랜

### 즉시 (P0)

1. JWT 역할 하드코딩 수정 (3개 파일)
2. GlobalRestControllerAdvice 중복 제거

### 1주 내 (P1)

3. 입력 유효성 검증 추가 (Bean Validation)
4. Refresh Token Redis 저장 구현
5. OAuth2AuthenticationSuccessHandlerTest 수정

### 2주 내 (P2)

6. 누락된 E2E 테스트 추가
7. Redis 테스트 컨텍스트 이슈 해결
8. UpdateMemberCommand 레이어 의존성 수정

---

## 6. 요약

| 구분               | 개수 |
| ------------------ | ---- |
| 🔴 Critical Issues | 4    |
| 🟡 High Priority   | 4    |
| 🟢 Medium Priority | 3    |
| ❌ 누락된 테스트   | 13+  |
| 📁 총 분석 파일    | 76개 |

전체적으로 기본 아키텍처와 코어 기능은 잘 구현되어 있습니다. 하지만 **JWT 역할 하드코딩** 문제가 여러 곳에서 발견되어 향후 권한 기반 기능 확장 시 심각한 장애가 될 수 있습니다. 또한 **입력 유효성 검증 부재**와 **테스트 커버리지 부족**이 프로덕션 배포 전 반드시 해결되어야 할 사항입니다.
