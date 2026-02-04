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
- [ ] **Member API**: Profile update, retrieval endpoints.
- [ ] **Token Refresh**: JWT refresh token logic (Redis).

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
