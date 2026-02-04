# 사용자 설정 필요 (TODO)

## 필수 설정 (Secrets)
이 프로젝트를 실행하기 위해 아래의 설정값들을 `application-local.yml` (로컬 개발용) 또는 운영 환경 변수로 설정해야 합니다.

### 1. Google OAuth 2.0 (Auth Module)
- **위치**: `module/auth/src/main/resources/application-local.yml`
- **설명**: 구글 로그인 기능을 위해 GCP 콘솔에서 발급받은 클라이언트 ID와 Secret이 필요합니다.
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

### 2. Apple OAuth 2.0 (Auth Module - Optional)
- **위치**: `module/auth/src/main/resources/application-local.yml`
- **설명**: 애플 로그인 기능이 필요한 경우 설정합니다.

### 3. JWT Secret Key (Auth Module)
- **위치**: `app/api/src/main/resources/application-local.yml` (현재는 더미 키가 설정되어 있음)
- **운영 환경**: `application-prod.yml`에서는 환경 변수 `JWT_SECRET_KEY`를 사용합니다.
- **설명**: 토큰 서명에 사용되는 비밀키입니다. 운영 환경에서는 반드시 강력한 랜덤 문자열로 교체하세요.

### 4. Database & Redis (App Module)
- **위치**: `app/api/src/main/resources/application-local.yml`
- **설명**: 로컬 Docker 환경을 사용하는 경우 기본 설정(`localhost`)이 작동하지만, 별도 DB 사용 시 URL/계정 정보를 수정하세요.

---
## 실행 방법 (Local)
1. 위 설정들을 `application-local.yml`에 적용합니다.
2. API 서버 실행:
   ```bash
   ./gradlew app:api:bootRun
   ```

---

# Development Roadmap & TODOs

## 1. Development Roadmap (Backend)

### Phase 1: Core Foundation (Completed)
- [x] **Project Structure**: Modular Monolith Setup (app, module, common, infra).
- [x] **Infrastructure**: MySQL, Redis, Mongo, Kafka configurations.
- [x] **Common Modules**: Security, Logger, I18n, Exception handling.
- [x] **Observability**: P6Spy, Request Logging.

### Phase 2: Auth & Member (In Progress)
- [x] **Auth Module**: OAuth2 (Google/Apple) integration with Spring Security.
- [x] **Member Module**: Entity design, Repository (DIP), Basic Service.
- [x] **Testing**: Unit tests for Auth service and handlers (Kotest).
- [x] **Member API**: Profile update, retrieval endpoints.
- [x] **Token Refresh**: JWT refresh token logic (Redis).

### Phase 3: Matching & Chat (Planned)
- [ ] **Matching Module**:
    - [ ] Daily recommendation algorithm (Batch).
    - [ ] Real-time random matching queue (Redis).
- [ ] **Chat Module**:
    - [ ] WebSocket (STOMP) setup.
    - [ ] MongoDB message storage.
    - [ ] Chat room management.

### Phase 4: Feed & Notification (Planned)
- [ ] **Feed Module**: Timeline, Post CRUD.
- [ ] **Notification Module**: FCM integration.

---

## 2. User Action Items (Required for Operations)

**Development cannot proceed to Production without these items.**

### A. Authentication Keys
- [ ] **Google OAuth**: Client ID & Secret 발급 (GCP Console).
- [ ] **Apple Sign In**: Service ID, Key ID, Private Key(.p8) 발급 (Apple Developer).

### B. Cloud Infrastructure
- [ ] **Oracle Cloud**: Instance (ARM) setup & SSH Key generation.
- [ ] **AWS S3**: Bucket creation & IAM User (Access Key/Secret) setup.
- [ ] **Firebase (FCM)**: Project creation & Service Account JSON download.

### C. Environment Configuration
- [ ] **Local**: `application-auth.yml`에 Dummy Key 대신 실제 발급받은 Test Key 적용 (Optional).
- [ ] **Production**: GitHub Secrets 또는 CI/CD 파이프라인에 위 Secret Key 등록.
