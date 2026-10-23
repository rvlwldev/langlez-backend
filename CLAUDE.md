# Langlez API

Spring Boot 4.1 + Kotlin 멀티모듈. 서비스를 `server/` 아래 Gradle 모듈로 하나씩 만들고, 서비스마다 컨테이너 하나로 배포한다.

## 병렬 작업은 git worktree로

병렬로 작업할 때는 같은 작업 트리에서 브랜치를 바꾸지 않고, 작업마다 새 브랜치와 worktree를 만든다.

- 경로는 `worktree/<브랜치명>`이다. `worktree/` 아래 폴더 이름이 곧 브랜치 이름이다. 브랜치명에 `/`가 있으면 그대로 하위 폴더가 된다 (`feat/chat` → `worktree/feat/chat`).
- 기준 브랜치는 따로 말이 없으면 `main`이다.

```
git worktree add -b <브랜치명> worktree/<브랜치명> main
```

- `api/` 루트의 작업 트리는 `main`에 두고, 병렬 작업은 거기서 하지 않는다.
- 작업은 만든 worktree 안에서 한다. Gradle도 그 안의 `server/`에서 실행한다 (`worktree/<브랜치명>/server`).
- `worktree/`는 `.gitignore` 대상이다. worktree 안의 변경은 그 브랜치에 커밋한다.
- Claude Code가 기본 위치에 worktree를 만드는 기능(`isolation: "worktree"`, `EnterWorktree` 등)은 쓰지 않고 위 명령으로 직접 만든다.
- 작업이 끝나면 `git worktree remove worktree/<브랜치명>`으로 정리한다. 브랜치 삭제는 확인받고 한다.

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

## 규칙

- 코드에 주석을 쓰지 않는다. 이름과 구조로 설명하고, Kotlin·Gradle·테스트 모두 똑같이 적용한다.
- `share/*` 모듈은 서로 의존하지 않는다. 각자 자동설정(`AutoConfiguration.imports`)으로 스스로 완결되고, 소비 모듈이 의존만 추가하면 독립적으로 뜬다.
- 의존을 피하려고 인터페이스를 만들지 않는다. 분산락과 캐시는 `share:redis`에 구현째로 있고, 쓰는 모듈이 `share:redis`를 직접 의존한다.
- 테스트는 Kotest `BehaviorSpec`(Given/When/Then)과 Kotest matcher로 쓴다. 스프링 컨텍스트가 필요하면 `SpringExtension`을 쓰고 JUnit `@Test`는 쓰지 않는다.
- `share/*` 모듈은 Testcontainers로 자기 기능을 검증하는 테스트를 자기 모듈 안에 둔다.
