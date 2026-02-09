# 코드 리뷰 보고서

**날짜:** 2026-02-05
**리뷰어:** Sisyphus (AI Agent)
**프로젝트:** Langlez Backend Server

---

## 1. 요약 (Summary)
본 프로젝트는 **Modular Monolith** 아키텍처와 Vertical Slices 구조를 견고하게 따르고 있습니다. 최근 수행된 리팩토링을 통해 보안 로직이 `common:security`로 성공적으로 통합되었으며, `Testcontainers`를 활용한 강력한 E2E 테스트 환경이 구축되었습니다.

| 카테고리 | 상태 | 요약 |
| :--- | :---: | :--- |
| **아키텍처** | ✅ 좋음 | 모듈 간 분리 및 의존성 방향성이 올바름. |
| **도메인 로직** | ✅ 좋음 | 풍부한 도메인 모델(Rich Domain Model) 사용. 서비스가 엔티티에 로직을 위임함. |
| **보안** | ⚠️ 개선 필요 | 로직은 안전하나, URL 설정 등 일부 정리 필요. |
| **테스트** | ✅ 훌륭함 | Testcontainers와 RestAssured를 활용한 실제 E2E 테스트 구축. |
| **코드 품질** | ⚠️ 경미한 이슈 | 일부 패키지 명명 규칙 불일치 및 접근 제어자 누락. |

---

## 2. 상세 발견 사항 (Detailed Findings)

### 🔴 Critical (0건)
*심각한 보안 취약점이나 아키텍처 위반 사항 없음.*

### 🟡 Major (3건)

#### 2.1. 보안 URL 설정 불일치
*   **위치**: `common/security/src/main/kotlin/com/langlez/security/config/SecurityConfig.kt`
*   **문제**: 이전 버전에서는 `requestMatchers`에 `/api/v1/auth/**`가 포함되어 있었으나, 현재는 `/api/auth/**`로 올바르게 수정되었습니다.
*   **상태**: ✅ 해결됨

#### 2.2. 설정 파일의 범용 패키지 명명
*   **위치**: `infra/mysql/.../com/langlez/config/MysqlConfiguration.kt`, `common/observability/.../com/langlez/config/GlobalMetricsConfiguration.kt`
*   **문제**: 여러 설정 클래스가 `com.langlez.config`라는 범용 패키지를 공유함. 이로 인해 테스트 시 컴포넌트 스캔 충돌이 발생했음.
*   **제안**: 모듈별로 패키지를 구체화하여 분리.
    *   `com.langlez.infra.mysql.config`
    *   `com.langlez.common.observability.config`

#### 2.3. MysqlConfiguration의 불필요한 Import
*   **위치**: `infra/mysql/.../MysqlConfiguration.kt`
*   **문제**: `@Import(DataSourceAutoConfiguration::class)`를 사용 중. 이는 Testcontainers나 다른 자동 설정 메커니즘을 방해할 수 있음.
*   **제안**: `@Import`를 제거하고 Spring Boot의 자동 설정에 맡기거나, 커스텀 로직이 필요하다면 DataSource 빈을 명시적으로 정의.

### 🟢 Minor (4건)

#### 3.1. 설정 클래스의 `open` 제어자 누락
*   **위치**: `common/observability/.../GlobalMetricsConfiguration.kt`
*   **문제**: Spring Configuration 클래스는 `open`이어야 함 (CGLIB 프록시). 현재는 닫힌 클래스임.
*   **제안**: 클래스와 `@Bean` 메서드에 `open` 키워드 추가 또는 해당 모듈에 `kotlin-spring` 플러그인 적용. (현재 수동으로 `open` 추가하여 해결됨)

#### 3.2. 애플리케이션 서비스 내 도메인 로직 존재
*   **위치**: `module/auth/.../CustomOAuth2UserService.kt`
*   **문제**: `generateAccountName` (랜덤 접미사 생성) 로직이 서비스 계층에 있음.
*   **제안**: 이 로직을 도메인 서비스(예: `AccountNameGenerator`)나 `Member`의 Companion Object로 이동.

#### 3.3. 리다이렉트 URI 기본값 하드코딩
*   **위치**: `common/security/.../OAuth2SuccessHandler.kt`
*   **문제**: 기본값이 `http://localhost:3000/oauth2/redirect`로 하드코딩됨.
*   **제안**: `application-prod.yml`에서 반드시 오버라이딩되도록 확인 필요. (현재 구조상 가능)

#### 3.4. EntityNotFoundException 처리 미흡
*   **위치**: `common/exception/.../GlobalRestControllerAdvice.kt`
*   **문제**: `CommonException`과 일반 `Exception`만 처리함. `MemberService` 등에서 사용하는 `EntityNotFoundException`에 대한 명시적 핸들러가 없어 500 에러로 처리될 수 있음.
*   **제안**: `EntityNotFoundException` 전용 핸들러 추가 (404 반환).
*   **상태**: ✅ 해결됨

---

## 3. 특정 로직 점검 (Specific Logic Checks)

| 점검 항목 | 결과 | 상세 내용 |
| :--- | :---: | :--- |
| **AuthService Refresh Token 검증** | ✅ 통과 | `AuthService`가 `JwtTokenProvider` 서명 검증과 `Redis` 저장 값 일치 여부를 모두 확인하고 있음. |
| **Member 낙관적 락(Optimistic Locking)** | ⚠️ 누락됨 | `Member` 엔티티에 `@Version` 필드가 없음. 동시 업데이트 시 데이터 덮어쓰기 발생 가능. |
| **TargetLanguageCommandDto 매핑** | ✅ 통과 | `MemberController`가 DTO를 `TargetLanguageCommandDto`로 변환하고, `MemberService`가 이를 도메인 엔티티로 올바르게 변환함. |

---

## 4. 다음 단계 (Action Items)

1.  **설정 패키지 리팩토링**: 스캔 충돌 방지를 위해 설정 클래스 패키지 이동.
2.  **낙관적 락 적용**: `Member` 엔티티에 `@Version val version: Long = 0` 필드 추가.
3.  **SecurityConfig 정리**: 레거시 URL 패턴 제거.
4.  **예외 처리 강화**: `GlobalRestControllerAdvice`에 `EntityNotFoundException` 핸들러 추가.
