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
./gradlew :app:bootJar                  # app/build/libs/app-0.0.1-SNAPSHOT.jar
```

- 테스트는 Testcontainers(Postgres, Mongo, Redis, Kafka)를 띄우므로 Docker가 떠 있어야 한다.
- 앱을 로컬에서 띄우려면 인프라가 필요하다: `api/` 루트에서 `./local-infra-start.sh` (docker/ 아래 compose 전부). `application.yml` 기본값이 이 compose의 포트·계정에 맞춰져 있다.
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
- PR이 merge되면 `git worktree remove worktree/<브랜치명>`으로 지운다. 브랜치 삭제는 확인받고 한다.

### legacy

`worktree/legacy`는 레거시 소스를 참고하려고 둔 `legacy` 브랜치의 worktree다. 읽기만 하고 고치지 않는다.

## 구조

```
server/   Gradle 멀티모듈
  app/      실행 가능한 앱 (bootJar). share 하위 모듈들을 조립한다
  share/    공통 라이브러리 모듈을 모아 두는 폴더 (자체는 모듈이 아님)
    auth/     Supabase JWT 검증 (Spring Security 리소스 서버)
    redis/    분산락(@DistributedLock), 캐시(CacheProvider)
    rdb/      JPA, Flyway, QueryDSL
    mongodb/  MongoDB 리포지토리
    kafka/    Kafka 리스너 컨테이너, 에러 핸들러, DLT
docker/   로컬 인프라 compose. 볼륨 데이터(docker/volume)는 git에서 제외
worktree/ git worktree 모음
```

## 아키텍처

여러 파일을 봐야 보이는 규약만 적는다.

- **`share/*`는 스스로 설정되는 라이브러리다.** 컴포넌트 스캔이 아니라 `META-INF/spring/...AutoConfiguration.imports`로 등록된다. 그래서 소비 모듈의 메인 클래스가 어느 패키지에 있어도 동작한다. 각 모듈의 테스트는 라이브러리와 다른 패키지(`com.langlez.consumer`)에 `ConsumerApp`을 두어 이를 일부러 검증한다.
- **패키지 규약이 설정에 박혀 있다.** `share:rdb`는 JPA 리포지토리를 `com.langlez.**.jpa`, `share:mongodb`는 Mongo 리포지토리를 `com.langlez.**.mongo` 패키지에서만 켠다. 엔티티 스캔은 앱 패키지 기준이라 앱 밖 패키지의 엔티티는 `@EntityScan`이 필요하다. Flyway 위치는 `classpath:migration`이다. `app`의 메인 클래스는 루트 패키지 `com.langlez`에 있어서 도메인 모듈들이 `com.langlez.<도메인>` 아래에 있으면 함께 스캔된다.
- **`share:redis`**:
  - 락 키는 `prefix + keys(SpEL) + @LockKey(파라미터 이름순)`이다. 락 실패 시 `throwOnFailure=true`면 `LockAcquireFailedException`, 아니면 `null`을 돌려주므로 반환 타입을 nullable로 둔다.
  - 캐시는 값을 Jackson 3(`tools.jackson`) JSON 문자열로 저장하고 읽을 때 호출자가 넘긴 `Class`로 역직렬화한다. 읽을 수 없는 값은 미스로 처리한다. Redis 실패 시 Caffeine으로 대체하고, 복구되면 로컬 값을 버린다.
  - 접속 정보는 `spring.data.redis.*`를 직접 읽는다 (Boot의 Redis 자동설정은 쓰지 않는다).
- **`share:auth`**: `langlez.auth.supabase-url`(필수)에서 JWKS와 발급자를 만든다. ES256/RS256만 허용하고 `iss`와 `aud`(기본 `authenticated`)를 검증한다. 익명 키 토큰을 막으려면 `aud` 검증이 필수다. `/error`와 `langlez.auth.public-paths`(기본 `/actuator/health/**`)만 열려 있고, 서비스가 자기 `SecurityFilterChain`을 정의하면 그쪽이 우선한다. 서블릿(WebMVC) 전용이다.
- **Boot 4 프로퍼티·패키지 이름**이 3.x와 다르다: Mongo URI는 `spring.mongodb.uri`, 자동설정 클래스는 `org.springframework.boot.<모듈>.autoconfigure.*`.
- **새 실행 서비스를 추가할 때**: `settings.gradle.kts`에 include하고, 루트 `build.gradle.kts`의 `bootJar` 조건(`project.path != ":app"`이면 비활성화)에 그 경로를 포함시켜야 jar가 만들어진다. 아직 Dockerfile과 Railway 배포 설정은 없다.

## 규칙

- 코드에 주석을 쓰지 않는다. 이름과 구조로 설명하고, Kotlin·Gradle·테스트 모두 똑같이 적용한다.
- `share/*` 모듈은 서로 의존하지 않는다. 각자 자동설정(`AutoConfiguration.imports`)으로 스스로 완결되고, 소비 모듈이 의존만 추가하면 독립적으로 뜬다.
- 의존을 피하려고 인터페이스를 만들지 않는다. 분산락과 캐시는 `share:redis`에 구현째로 있고, 쓰는 모듈이 `share:redis`를 직접 의존한다.
- 테스트는 Kotest `BehaviorSpec`(Given/When/Then)과 Kotest matcher로 쓴다. 스프링 컨텍스트가 필요하면 `SpringExtension`을 쓰고 JUnit `@Test`는 쓰지 않는다.
- `share/*` 모듈은 Testcontainers로 자기 기능을 검증하는 테스트를 자기 모듈 안에 둔다.
