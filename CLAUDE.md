# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Langlez API

Spring Boot 4.1 + Kotlin 멀티모듈. 서비스를 `server/` 아래 Gradle 모듈로 하나씩 만들고, 서비스마다 컨테이너 하나로 배포한다. 레거시 소스는 `legacy` 브랜치에만 남아 있고 참고용이다.

## 명령어

Gradle은 항상 `server/`에서 실행한다 (`api/` 루트에는 Gradle 빌드가 없다). JDK 21 toolchain, Gradle 9.2.1 wrapper.

```
cd server
./gradlew build                         # 전체 빌드 + 전체 테스트
./gradlew :share:redis:test             # 모듈 하나의 테스트
./gradlew :share:auth:test --tests "com.langlez.consumer.AuthSupportTest"   # 테스트 클래스 하나
SUPABASE_URL=https://<project-ref>.supabase.co ./gradlew :app:bootRun       # 앱 실행
LANGLEZ_LOGGER_LOKI_PUSHURL=http://localhost:3100/loki/api/v1/push ...       # 로그를 로컬 Loki로도 보낼 때
./gradlew :app:bootJar                  # app/build/libs/app-0.0.1-SNAPSHOT.jar
```

- 테스트는 Testcontainers(Postgres, Mongo, Redis, Kafka)를 띄우므로 Docker가 떠 있어야 한다.
- 앱을 로컬에서 띄우려면 인프라가 필요하다: `docker/start.sh` (docker/ 아래 compose 전부, 모니터링 포함). 이미 떠 있어도 다시 실행해도 된다. `application.yml` 기본값이 이 compose의 포트·계정에 맞춰져 있다.
- 로컬 MinIO(S3 호환, 운영은 Cloudflare R2): API `http://localhost:9000`, 콘솔 `http://localhost:9001` (`langlez` / `langlez-local`). `start.sh`가 버킷 `langlez-attachments`를 만들고 익명 `s3:GetObject`만 허용한다(R2 공개 버킷처럼 익명 list·PUT은 403, 정책은 `docker/config/minio/public-read.json`). 공식 이미지는 더 못 받아서 Chainguard 이미지를 digest로 고정해 쓴다.
- 앱은 `SUPABASE_URL` 환경변수가 없으면 기동에 실패한다 (`langlez.auth.supabase-url`에 기본값이 없다).
- 루트 `build.gradle.kts`의 `bootRun`은 JVM 시스템 프로퍼티 중 `spring.*`만 앱에 넘긴다.

## langlez 하네스 (`/code`)

worktree, PR, 서브에이전트 구현, 리뷰, merge로 이어지는 흐름은 `.claude/skills/code/`에 있다. 사용자가 `/code`를 부른 세션에서만 이 흐름을 따른다. 부르지 않았으면 하네스를 쓰지 않고, 서브에이전트 없이 `api/` 루트에서 직접 작업한다.

## git worktree

worktree를 쓸 때(`/code` 세션이나 사용자가 요청한 병렬 작업)는 아래를 따른다.

- 경로는 `worktree/<브랜치명>`이다. `worktree/` 아래 폴더 이름이 곧 브랜치 이름이다. 브랜치명에 `/`가 있으면 그대로 하위 폴더가 된다 (`feat/chat` → `worktree/feat/chat`).
- 기준 브랜치는 따로 말이 없으면 `main`이다.

```
git worktree add -b <브랜치명> worktree/<브랜치명> main
```

- `api/` 루트의 작업 트리는 `main`에 둔다. PR이 merge되면 루트에서 `git pull --ff-only`로 따라간다.
- 작업은 만든 worktree 안에서 한다. Gradle도 그 안의 `server/`에서 실행한다 (`worktree/<브랜치명>/server`).
- `worktree/`는 `.gitignore` 대상이다. worktree 안의 변경은 그 브랜치에 커밋한다.
- Claude Code가 기본 위치에 worktree를 만드는 기능(`isolation: "worktree"`, `EnterWorktree` 등)은 쓰지 않고 위 명령으로 직접 만든다.
- PR이 merge되면 `git worktree remove worktree/<브랜치명>`으로 지운다. 브랜치 삭제는 `/code` 세션에서는 merge 뒤 바로 하고, 그 밖에는 확인받고 한다.

### legacy

`worktree/legacy`는 레거시 소스를 참고하려고 둔 `legacy` 브랜치의 worktree다. 읽기만 하고 고치지 않는다.

## 구조

```
server/   Gradle 멀티모듈
  app/      실행 가능한 앱 (bootJar). share 하위 모듈들을 조립한다
  share/    공통 라이브러리 모듈을 모아 두는 폴더 (자체는 모듈이 아님)
    auth/     Supabase JWT 검증 (Spring Security 리소스 서버)
    redis/    분산락(@DistributedLock), 캐시(CacheProvider)
    jackson/  Jackson 3 JsonMapper (직렬화 규칙의 단일 출처)
    logger/   kotlin-logging, Loki 전송
    rdb/      JPA, Flyway, QueryDSL
    mongodb/  MongoDB 리포지토리
    kafka/    Kafka 리스너 컨테이너, 에러 핸들러, DLT
    web/      다국어 메시지(i18n), 전역 예외 처리, 에러 응답 형식
docker/   로컬 인프라 compose. 볼륨 데이터(docker/volume)는 git에서 제외
worktree/ git worktree 모음
```

## 아키텍처

여러 파일을 봐야 보이는 규약만 적는다.

- **`share/*`는 스스로 설정되는 라이브러리다.** 컴포넌트 스캔이 아니라 `META-INF/spring/...AutoConfiguration.imports`로 등록된다. 그래서 소비 모듈의 메인 클래스가 어느 패키지에 있어도 동작한다. 각 모듈의 테스트는 라이브러리와 다른 패키지(`com.langlez.consumer`)에 `ConsumerApp`을 두어 이를 일부러 검증한다.
- **리포지토리와 엔티티는 앱 메인 클래스 패키지 아래면 어디든 둔다.** JPA·Mongo 리포지토리와 엔티티 스캔은 모두 Boot 기본값(메인 클래스 패키지 기준)이다. JPA와 Mongo가 함께 있으면 Spring Data가 서로의 리포지토리마다 "Could not safely identify store assignment" INFO 로그를 남긴다. `share:rdb`가 `EnvironmentPostProcessor`(`RdbDefaultProperties`)로 `RepositoryConfigurationExtensionSupport` 로그 레벨 기본값을 WARN으로 넣어 이를 막는다. 앱이 같은 프로퍼티를 정하면 앱 값이 이긴다. Flyway 위치는 `classpath:migration`이다. `app`의 메인 클래스는 루트 패키지 `com.langlez`에 있어서 도메인 모듈들이 `com.langlez.<도메인>` 아래에 있으면 함께 스캔된다.
- **`share:redis`**:
  - 락 키는 `prefix + keys(SpEL) + @LockKey(파라미터 이름순)`이다. 락 실패 시 `throwOnFailure=true`면 `LockAcquireFailedException`, 아니면 `null`을 돌려주므로 반환 타입을 nullable로 둔다.
  - 캐시는 값을 Spring의 `JsonMapper` 빈(`share:jackson`)으로 JSON 문자열을 만들어 저장하고 읽을 때 호출자가 넘긴 `Class`로 역직렬화한다. 읽을 수 없는 값은 미스로 처리한다. Redis 실패 시 Caffeine으로 대체하고, 복구되면 로컬 값을 버린다.
  - 접속 정보는 `spring.data.redis.*`를 직접 읽는다 (Boot의 Redis 자동설정은 쓰지 않는다).
- **`share:auth`**: `langlez.auth.supabase-url`(필수)에서 JWKS와 발급자를 만든다. ES256/RS256만 허용하고 `iss`와 `aud`(기본 `authenticated`)를 검증한다. 익명 키 토큰을 막으려면 `aud` 검증이 필수다. `/error`와 `langlez.auth.public-paths`(기본 `/actuator/health/**`)만 열려 있고, 서비스가 자기 `SecurityFilterChain`을 정의하면 그쪽이 우선한다. 서블릿(WebMVC) 전용이다.
- **actuator는 관리 포트(`app`은 9091)에만 있다.** 인증 필터는 관리 포트에도 걸리므로 `app`의 `public-paths`에 liveness, readiness, `/actuator/prometheus`를 넣어 연다. 서비스 포트(8080)에는 actuator가 없어서 밖에서는 보이지 않는다. 배포할 때 관리 포트를 외부에 열지 않고, Prometheus는 내부망으로 긁는다.
- **Jackson 버전은 루트 `build.gradle.kts`가 정한다.** `jackson-bom.version`(3.x)과 `jackson-2-bom.version`(2.x, Jackson 3가 쓰는 `jackson-annotations` 포함)을 version catalog 값으로 덮어써서 모든 모듈이 같은 버전을 쓴다. Jackson은 `tools.jackson.databind.json.JsonMapper` 빈을 주입받아 쓴다 (`com.fasterxml.jackson.databind.ObjectMapper`는 Jackson 2라 빈이 없다).
- **`share:logger`**: `langlez.logger.loki.push-url`이 있을 때만 루트 로거에 Loki appender를 붙인다. 라벨은 `service`(=`spring.application.name`)와 `level`이고, 본문은 MDC(`traceId` 등)를 담은 JSON이다. 빈이 만들어지기 전의 기동 로그는 보내지 않고, 종료할 때 남은 로그는 보낸다. Grafana Cloud면 `username`/`password`를 함께 준다.
- **`attachment`**: 업로드는 `staging/` 접두사로 받고, 완료 확인 때 검증된 파일만 최종 key로 복사한다. 운영 버킷(R2)에는 `staging/` 접두사를 1일 뒤 만료시키는 수명주기 규칙을 건다. 최종 key 파일은 지우지 않는다.
- **`share:web`**: 서블릿(WebMVC) 전용이고 의존만 추가하면 뜬다.
  - i18n: 지원 언어는 `ko`, `en`, `ja`, `zh_CN`, `zh_TW`, `es`, `fr`, `de`, `pt`, `ru`, `vi`, `id` 12개이고 언어는 `Accept-Language`로만 정한다 (`zh-Hant`·`zh-TW` → `zh_TW`, `zh`·`zh-Hans` → `zh_CN`). 헤더가 없거나 지원하지 않는 언어는 시스템 로케일이 아니라 `en`이다. 메시지 파일은 각 모듈이 `src/main/resources/i18n/<모듈>/messages_<언어>.properties`에 두고, `share:web`이 `classpath*:i18n/*/messages*.properties`를 모두 찾아 `messageSource` 빈 하나로 묶는다 (UTF-8). 어떤 언어 파일에 키가 없거나 언어 파일 자체가 없으면 `en` 문장으로 대체하므로 모듈마다 `messages_en.properties`를 둔다 (없으면 요청마다 WARN이 남는다). 소비 모듈은 따로 설정하지 않는다.
  - 키는 모듈마다 Kotlin 상수 객체 `I18N`(`I18N.<DOMAIN>.<NAME>`)으로 쓰고, 값이 properties 키다 (`common.not-found` 같은 소문자·점·하이픈). `share:web`의 공통 키는 `com.langlez.web.i18n.I18N.COMMON`에 있다. 도메인 키(`member.*` 등)는 그 모듈의 properties로 둔다. 인자가 있는 문장은 `MessageFormat`이라 작은따옴표를 `''`로 써야 한다.
  - 의도한 에러는 `LanglezException(status, key, vararg args, cause)`(`com.langlez.web.exception`)로 던진다. 모든 에러 응답 body는 `Accept`와 상관없이 항상 `application/json`의 `{"error": true, "message": "<요청 언어로 번역된 문장>"}`이고 상태는 HTTP 상태 코드로만 전달한다. `@Valid`·`@Validated` 검증 메시지는 애노테이션에 `message = "{키}"`로 쓰면 같은 `MessageSource`로 번역되고, 응답에는 필드나 파라미터 이름을 붙여 `이름: 문장`으로 이어 붙인다. 검증 애노테이션 메시지는 항상 `{키}`로 쓴다 (안 쓰면 Hibernate Validator가 JVM 로케일 문장을 쓴다). Boot의 `/error`(필터 등 컨트롤러 밖에서 난 에러)도 `JsonErrorController`와 `ErrorAttributes`로 같은 형식으로 나간다.
  - advice는 `LanglezException`·`ResponseStatusException`(`IntendedExceptionHandler`), Security·DAO·Bean Validation, 나머지(`GlobalExceptionHandler`) 순서로 검사된다. Spring이 advice마다 예외의 `cause`까지 따라가며 핸들러를 찾으므로 이 둘은 `cause`와 상관없이 자기 상태·키·로그 규칙을 따른다. 반대로 일반 예외가 감싼 예외는 `cause`를 따라 처리된다. `IllegalStateException(cause = DataIntegrityViolationException)`은 500이 아니라 409이고, 검증 예외를 감싸면 400이고, `LanglezException`을 감싸면 그 예외의 상태다. 이때 감싼 쪽의 ERROR 로그는 남지 않는다. `HandlerMethodValidationException`은 `ResponseStatusException`의 하위 클래스라서 `IntendedExceptionHandler`에 함께 둔다. `ResponseStatusException` 하위 클래스를 따로 처리하려면 그 핸들러도 여기에 둬야 한다.
  - 로그: 4xx는 `ConstraintViolationException`을 포함해 어떤 advice도 아무것도 남기지 않는다. 5xx `LanglezException`·5xx `ResponseStatusException`·5xx `ErrorResponse`·처리되지 않은 예외는 ERROR로 스택까지 남긴다.
  - Spring Security(`AuthenticationException`·`AccessDeniedException` → 401·403), DAO(`DataIntegrityViolationException`·낙관적 락 실패 → 409), Bean Validation(클래스 레벨 `@Validated` 컨트롤러가 던지는 `ConstraintViolationException` → 400, 서비스 계층에서 난 같은 예외도 400이다) 처리는 그 클래스가 classpath에 있을 때만 켜진다 (`compileOnly` + `@ConditionalOnClass`). `ErrorResponse`를 구현한 Spring MVC 예외(406, multipart 파트 누락 등)와 `ResponseStatusException`은 자기 상태 코드를 쓰고 메시지는 상태→키 매핑(`statusKey`)으로 정한다. `MultipartException`(multipart가 아닌 요청 등)은 400이다. 그 밖의 예외만 500이다.
- **Boot 4 프로퍼티·패키지 이름**이 3.x와 다르다: Mongo URI는 `spring.mongodb.uri`, 자동설정 클래스는 `org.springframework.boot.<모듈>.autoconfigure.*`.
- **새 실행 서비스를 추가할 때**: `settings.gradle.kts`에 include하고, 루트 `build.gradle.kts`의 `bootJar` 조건(`project.path != ":app"`이면 비활성화)에 그 경로를 포함시켜야 jar가 만들어진다. 아직 Dockerfile과 Railway 배포 설정은 없다.

## 규칙

- 코드에 주석을 쓰지 않는다. 이름과 구조로 설명하고, Kotlin·Gradle·테스트 모두 똑같이 적용한다.
- Kotlin 문자열에 `${`를 글자 그대로 넣을 때(`@Value`의 프로퍼티 자리표시자 등)는 `"\${...}"` 대신 `$$"${...}"`로 쓴다.
- 서비스 모듈(`member` 같은 도메인 모듈)끼리는 서로 의존하지 않는다. 서비스 모듈은 `share/*`만 의존한다.
- `share/*` 모듈은 각자 자동설정(`AutoConfiguration.imports`)으로 스스로 완결되고, 소비 모듈이 의존만 추가하면 뜬다.
- 의존을 피하려고 인터페이스를 만들지 않는다. 분산락과 캐시는 `share:redis`에 구현째로 있고, 쓰는 모듈이 `share:redis`를 직접 의존한다.
- 테스트는 Kotest `BehaviorSpec`(Given/When/Then)과 Kotest matcher, MockK, Testcontainers, 스프링 테스트(`spring-test`, `spring-boot-test`, `SpringExtension`)만으로 쓴다. 컨테이너는 코드에서 직접 시작하고, 비동기 대기는 Kotest `eventually`를 쓴다.
- JUnit Jupiter(`@Test`), Mockito, AssertJ, Hamcrest, Awaitility, JSONassert, XMLUnit, Testcontainers의 JUnit 연동(`@Testcontainers`, `@Container`)은 쓰지 않는다. 루트 `build.gradle.kts`가 이들을 테스트 의존에서 제외하므로 import하면 컴파일이 안 된다. JUnit Platform(launcher, engine)은 Kotest가 그 위에서 돌아서 남아 있다.
- `share/*` 모듈은 Testcontainers로 자기 기능을 검증하는 테스트를 자기 모듈 안에 둔다.
- Flyway 마이그레이션은 모듈의 `src/main/resources/migration/`에 두고, 모듈 간 버전 충돌을 피하려고 날짜 버전을 쓴다 (`V2026_10_24_1__create_attachments.sql`).
