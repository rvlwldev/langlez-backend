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

## TODO

### 공통
- [ ] 코루틴 제거 (Virtual Threads로 통일)
- [ ] 다국어 에러 메시지 정리
- [ ] 컨트롤러 DTO 표준화

### Auth
- [x] 컨트롤러용 어노테이션 (@MemberID, @MemberRole)
- [ ] 토큰 TTL 구체화 (액세스 15분, 리프레시 7일)
- [ ] 로그아웃 (토큰 블랙리스트)
- [ ] RTR (Refresh Token Rotation)

### Member
- [ ] 온라인 상태 조회 (Redis 30분 TTL)

### Profile
- [ ] 캐시 설정
- [ ] 방문자 수 비동기 적재
- [ ] 이미지 정렬 및 썸네일 생성

### Relationship
- [ ] 타겟을 username으로 변경
- [ ] Repository 구현 완성

### 미착수 모듈
- chat, language, report, feed, location, match
- interest, call (WebRTC), space (보이스룸)
- notification, subscription (IAP), dashboard
