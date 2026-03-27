# Langlez Backend

언어 학습 소셜 네트워킹 플랫폼 백엔드

## 기술 스택

- **Framework**: Spring Boot 3.x + Kotlin + Java 21 (Virtual Threads)
- **Database**: MySQL (JPA + QueryDSL), MongoDB, Redis (Redisson)
- **Messaging**: Apache Kafka (OutBox 패턴)
- **Storage**: AWS S3 (prod) / Local Filesystem (local)
- **Auth**: JWT + OAuth2 (Google, Apple)
- **Monitoring**: Prometheus, P6Spy

## 프로젝트 구조

```
langlez-backend/
├── app/api                     # Spring Boot 진입점
├── core/                       # 공유 인터페이스 (OutBoxEventPublisher)
├── common/
│   ├── exception/              # LanglezException, ExceptionResponse
│   ├── jackson/                # JSON 직렬화 설정
│   ├── observability/          # P6Spy, Prometheus, 쿼리 로거
│   ├── security/               # JWT, OAuth2, 필터, @MemberID, @MemberRole
│   └── web/                    # Swagger, GlobalRestControllerAdvice, i18n
├── infra/
│   ├── files/                  # FileStorage (S3, Local)
│   ├── kafka/                  # KafkaConfiguration
│   ├── mongo/                  # MongoDB 트랜잭션, 감사
│   ├── mysql/                  # MySQLConfiguration, JPAQueryFactory
│   └── redis/                  # Redisson, ResilientCache, @DistributedLock
└── module/
    ├── auth/                   # OAuth2 인증, 토큰 갱신
    ├── member/                 # 회원 CRUD, 캐싱, 이벤트 발행
    ├── outbox/                 # OutBox 패턴 (5초 폴링, Kafka 발행)
    ├── profile/                # 프로필 + 이미지 관리
    └── relationship/           # 팔로우 / 블록
```

## 모듈 아키텍처 (각 모듈 내부)

```
api → application → domain ← infrastructure
```

- **Domain**: 순수 비즈니스 로직 (Spring import 최소화)
- **Application**: @Service, @Transactional, 이벤트 발행
- **Infrastructure**: Repository 구현체, JPA 위임, 캐싱
- **API**: @RestController, DTO (비즈니스 로직 없음)

## 빌드 및 실행

```bash
./gradlew build                           # 전체 빌드
./gradlew :app:api:bootRun               # 앱 실행 (local 프로파일)
./gradlew :module:<name>:test            # 모듈별 테스트
```

## 설정 및 실행

```bash
./infra-start.sh                          # 인프라 컨테이너 실행
./gradlew :app:api:bootRun               # API 서버 실행
```

자세한 로드맵과 TODO는 프로젝트 루트의 `PLAN.md`를 참고하세요.
