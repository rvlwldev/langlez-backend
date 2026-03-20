# 사용자 설정 필요 (TODO)

## 필수 설정 (Secrets)

이 프로젝트를 실행하기 위해 아래 설정값들을 `app/api/src/main/resources/application.yml` (로컬 개발용) 또는
운영 환경 변수로 설정해야 합니다.
현재는 통합된 `application.yml` 하나와 `application-production.yml` 두 가지만 사용합니다.

### 1. Google OAuth 2.0
- **위치**: `app/api/src/main/resources/application.yml`
- **설명**: GCP 콘솔에서 발급받은 클라이언트 ID와 Secret이 필요합니다.
- **설정 항목**:
  ```yaml
  spring:
    security:
      oauth2:
        client:
          registration:
            google:
              client-id: "YOUR_GOOGLE_CLIENT_ID"
              client-secret: "YOUR_GOOGLE_CLIENT_SECRET"
  ```

### 2. Apple OAuth 2.0 (선택)
- **위치**: `app/api/src/main/resources/application.yml`
- **설명**: Apple 로그인이 필요한 경우 설정합니다. Apple Developer 콘솔에서 Service ID, Key ID, .p8 형식의 Private Key가 필요합니다.

### 3. JWT Secret Key
- **위치**: `app/api/src/main/resources/application.yml` (현재는 로컬 개발용 더미 키가 설정되어 있음)
- **운영 환경**: `application-production.yml`에서는 환경 변수 `JWT_SECRET_KEY`를 사용합니다.
- **설명**: 토큰 서명에 사용되는 비밀키입니다. 운영 환경에서는 반드시 강력한 랜덤 문자열(Base64 인코딩)로 교체하세요.
- **생성 예시**: `openssl rand -base64 64`

### 4. OAuth2 리다이렉트 URI
- **위치**: `app/api/src/main/resources/application.yml`
- **설명**: OAuth2 로그인 성공 후 토큰을 전달할 프론트엔드 URL입니다. 이 값은 개발자가 직접 정의하고, 동일한 URL을 Google/Apple 콘솔의 "승인된 리다이렉트 URI" 목록에 등록해야 합니다.
- **설정 항목**:
  ```yaml
  app:
    oauth2:
      redirect-uri: "http://localhost:3000/oauth2/callback"  # 로컬 프론트엔드 주소
  ```
- **동작 방식**: 로그인 성공 시 `{redirect-uri}?accessToken=...&refreshToken=...` 형태로 프론트엔드에 전달됩니다.

### 5. 인프라 접속 정보
- **위치**: `app/api/src/main/resources/application.yml`
- **설명**: 로컬 Docker 환경(`./infra-start.sh`)을 사용하면 기본값이 그대로 동작합니다.
- **포트 정보**:
  - MySQL: `localhost:3306` (DB: `langlez_db`, user: `admin`, pw: `admin`)
  - MongoDB: `localhost:27017`
  - Redis Sentinel: `localhost:26380, 26381, 26382` (Master: `6379`)
  - Kafka: `localhost:9001, 9002, 9003`

---

## 실행 방법 (Local)

### 1. 인프라 컨테이너 실행
```bash
./infra-start.sh
```

### 2. API 서버 실행
```bash
./gradlew :app:api:bootRun
```

---

# 개발 로드맵 및 분석 리포트

## 1. 현재 코드 분석 결과

### 수정 완료된 항목

#### ~~JWT 설정 키 불일치~~ ✅
`application.yml` 키 이름을 코드(`jwt.access-token-ttl-secs`, `jwt.refresh-token-ttl-secs`)에 맞게 통일.

#### ~~`app.oauth2.redirect-uri` 미설정~~ ✅
`application.yml`에 로컬 개발용, `application-production.yml`에 환경 변수 기반 설정 추가.

#### ~~JWT Refresh Token 타입 검증 없음~~ ✅
`AuthService.refresh()`에서 `jwt.extractTokenType(token)`으로 "refresh" 타입 검증 추가.

#### ~~OAuth2 계정 식별 로직 취약점~~ ✅
`AuthService.loadUser()`에서 `findByProvider(providerId, providerType)` 우선 조회 후, 이메일 충돌 시 `auth.email-conflict` 에러 반환.

#### ~~LocalFileStorage Path Traversal 위험~~ ✅
`canonicalPath` 검증 및 `..` 제거 로직 추가.

#### ~~OutBox `publish()` 트랜잭션 미보장~~ ✅
`@Transactional(propagation = Propagation.MANDATORY)` 추가. 트랜잭션 외부 호출 시 예외 발생.

#### ~~OutBox 대량 이벤트 OOM 위험~~ ✅
`findTargetToDispatch(500)`으로 배치 크기 제한.

#### ~~HandlerExceptionResolver 순환 참조~~ ✅
`JwtAuthenticationFilter`, `WebSecurityConfiguration`에 `@Lazy @Qualifier("handlerExceptionResolver")` 추가.

#### ~~Logback XML 파싱 오류~~ ✅
`logback-spring.xml`의 잘못된 닫는 태그 수정 (`</balancing>` → `</neverBlock>`).

#### ~~S3FileStorage 프로필 불일치~~ ✅
`@Profile("prod")` → `@Profile("production")` 수정.

---

## 2. 백엔드 로드맵

### Phase 1: 핵심 기반 구축 ✅
- [x] **프로젝트 구조**: Modular Monolith 설정 (app, module, common, infra, core)
- [x] **인프라 설정**: Docker Compose 기반 로컬 환경 구성 (MySQL, MongoDB, Redis Sentinel, Kafka, Monitoring)
- [x] **공통 모듈**: Security, i18n (12개 언어), 예외 처리, 관찰가능성 (P6Spy, Prometheus)
- [x] **OutBox 패턴**: 분산 이벤트 발행 (5초 폴링, Redisson 분산락, Kafka 발행, 아카이빙)
- [x] **캐시 계층**: Redis(Redisson) + Caffeine 로컬 폴백 (ResilientCache)

### Phase 2: 인증 및 회원 ✅
- [x] **인증 모듈**: Spring Security + OAuth2 연동, `OAuth2SuccessHandler` 구현
- [x] **토큰 갱신**: JWT + Redis 기반 Refresh Token 저장 및 갱신 로직
- [x] **보안 수정**: JWT Refresh Token 타입 검증, OAuth2 계정 식별 (provider 기반), Path Traversal 방어
- [x] **OutBox 안정성**: 트랜잭션 필수화(`MANDATORY`), 배치 크기 제한
- [x] **Member API**: 내 정보 조회/수정, 다른 회원 조회
- [x] **Relationship API**: Follow/Unfollow/Block/Unblock + 커서 기반 페이지네이션 (무한 스크롤)
- [x] **설정 수정**: JWT 키 통일, OAuth2 redirect-uri 추가, logback XML 수정, 순환 참조 해결
- [x] **테스트 코드**: AuthService, MemberController, RelationshipService, RelationshipController 단위 테스트

### Phase 3: 매칭 및 채팅 (계획됨)
- [ ] **매칭 모듈**: Redis 기반 큐 및 일별 추천 알고리즘
- [ ] **채팅 모듈**: STOMP + MongoDB 메시징

### Phase 4: 피드 및 알림 (계획됨)
- [ ] **피드 모듈**: 게시글 CRUD 및 타임라인
- [ ] **알림 모듈**: FCM 연동

---

## 3. 운영 환경 준비 액션 아이템

### A. 인증 키 발급
- [ ] **Google OAuth**: GCP 콘솔에서 Client ID & Secret 발급, 리다이렉트 URI 등록
- [ ] **Apple Sign In**: Apple Developer 콘솔에서 Service ID, Key ID, Private Key(.p8) 발급

### B. 클라우드 인프라 구성
- [ ] **서버**: 인스턴스 생성 및 SSH Key 설정
- [ ] **AWS S3** (또는 동등 서비스): 버킷 생성 및 IAM Access Key/Secret 발급
- [ ] **Firebase (FCM)**: 프로젝트 생성 및 Service Account JSON 다운로드

### C. 운영 환경 설정
- [ ] `application-production.yml`에 인프라 접속 정보 입력 (현재 주석 처리됨)
- [ ] CI/CD 파이프라인(GitHub Actions 등)에 Secret Key 등록
  - `JWT_SECRET_KEY`
  - `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
  - `APPLE_CLIENT_ID`, `APPLE_CLIENT_SECRET`
  - DB 접속 정보, Redis 접속 정보, Kafka 접속 정보
