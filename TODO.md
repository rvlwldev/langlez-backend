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

### 🚨 즉시 수정 필요 (Critical)

#### JWT 설정 키 불일치 — 앱 시작 불가
`JwtParser.kt`에서 읽는 프로퍼티 키와 `application.yml`에 정의된 키 이름이 다릅니다.
런타임 시 `IllegalArgumentException: Could not resolve placeholder` 오류로 앱이 시작되지 않습니다.

- **코드에서 읽는 키**: `jwt.access-token-ttl-secs`, `jwt.refresh-token-ttl-secs`
- **yml에 정의된 키**: `jwt.access-token-validity-in-seconds`, `jwt.refresh-token-validity-in-seconds`
- **수정 방법**: application.yml의 키 이름을 코드에 맞게 통일

#### `app.oauth2.redirect-uri` 미설정
`OAuth2SuccessHandler`가 `@Value("${app.oauth2.redirect-uri}")` 값을 요구하지만, 어떤 yml에도 정의되어 있지 않습니다.
OAuth2 로그인 시 Bean 생성 실패로 앱이 시작되지 않습니다.
위의 설정 항목 4번 참조.

---

### ⚠️ 보안 취약점 (Security)

#### JWT Refresh Token 타입 검증 없음
`AuthService.refresh()`에서 `jwt.extractID(token)`만 호출할 뿐, 토큰의 `type` 클레임("refresh" 여부)을 검증하지 않습니다.
`JwtParser.extractTokenType()`이 이미 구현되어 있으므로, `refresh()` 진입 시점에 타입 검증 로직 추가가 필요합니다.

#### OAuth2 계정 식별 로직 취약점
`AuthService.loadUser()`에서 이메일만으로 회원을 조회합니다 (`repo.findByEmail(email)`).
Google과 Apple이 동일한 이메일을 반환할 경우, 다른 Provider로 가입한 계정에 로그인될 수 있습니다.
`(email + provider)` 복합 조건으로 조회하도록 수정이 필요합니다.

#### LocalFileStorage Path Traversal 위험
`LocalFileStorage`에서 `folder` 파라미터에 대한 경로 검증이 없어 `../` 등의 입력으로 상위 디렉토리 파일에 접근할 수 있습니다.

---

### 📉 운영 및 성능 위험 (Operational)

#### OutBox `publish()` 트랜잭션 미보장
`OutBoxService.publish()`에 `@Transactional`이 없습니다.
호출하는 Service(예: `MemberService`)가 `@Transactional`을 선언하고 있으므로 현재는 같은 트랜잭션에 참여하지만,
`publish()`가 트랜잭션 외부에서 호출될 경우 메인 로직은 성공하고 이벤트 저장만 실패하는 데이터 불일치가 발생할 수 있습니다.

#### OutBox 대량 이벤트 OOM 위험
`OutBoxService.dispatchEvents()`에서 `repo.findAllTargetToDispatch()`로 모든 이벤트를 한 번에 메모리에 적재합니다.
이벤트가 대량으로 쌓일 경우 OOM(Out Of Memory) 위험이 있습니다. 페이지네이션 처리가 필요합니다.

---

## 2. 백엔드 로드맵

### Phase 1: 핵심 기반 구축 (완료)
- [x] **프로젝트 구조**: Modular Monolith 설정 (app, module, common, infra, core)
- [x] **인프라 설정**: Docker Compose 기반 로컬 환경 구성 (MySQL, MongoDB, Redis Sentinel, Kafka, Monitoring)
- [x] **공통 모듈**: Security, i18n (12개 언어), 예외 처리, 관찰가능성 (P6Spy, Prometheus)
- [x] **OutBox 패턴**: 분산 이벤트 발행 (5초 폴링, Redisson 분산락, Kafka 발행, 아카이빙)
- [x] **캐시 계층**: Redis(Redisson) + Caffeine 로컬 폴백 (ResilientCache)

### Phase 2: 인증 및 회원 (⚠️ Critical 수정 후 완료 가능)
- [x] **인증 모듈**: Spring Security + OAuth2 연동, `OAuth2SuccessHandler` 구현 완료
- [x] **토큰 갱신**: JWT + Redis 기반 Refresh Token 저장 및 갱신 로직 구현
- [x] **관계 모듈**: Follow / Block 도메인 구현
- [ ] **Critical 수정**: JWT 설정 키 불일치 수정 (앱 시작 불가 버그)
- [ ] **Critical 수정**: `app.oauth2.redirect-uri` 설정 추가
- [ ] **보안 수정**: JWT Refresh Token 타입 검증 추가
- [ ] **보안 수정**: OAuth2 계정 식별 로직 (email + provider) 복합 조건으로 변경
- [ ] **신규 구현**: Member API 엔드포인트 (`MemberController`) — 현재 API 레이어 없음
- [ ] **신규 구현**: Social API 엔드포인트 (Follow/Block) — 도메인만 존재, API 레이어 없음

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
