# 사용자 설정 필요 (TODO)

## 필수 설정 (Secrets)
이 프로젝트를 실행하기 위해 아래의 설정값들을 `app/api/src/main/resources/application.yml` (로컬 개발용) 또는 운영 환경 변수로 설정해야 합니다.
현재는 통합된 `application.yml` 하나와 `application-production.yml` 두 가지만 사용합니다.

### 1. Google OAuth 2.0 (Auth Module)
- **위치**: `app/api/src/main/resources/application.yml`
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
- **위치**: `app/api/src/main/resources/application.yml`
- **설명**: 애플 로그인 기능이 필요한 경우 설정합니다. 서비스 ID, 키 ID 등이 필요합니다.

### 3. JWT Secret Key (Auth Module)
- **위치**: `app/api/src/main/resources/application.yml` (현재는 로컬 개발용 더미 키가 설정되어 있음)
- **운영 환경**: `application-production.yml`에서는 환경 변수 `JWT_SECRET_KEY`를 사용합니다.
- **설명**: 토큰 서명에 사용되는 비밀키입니다. 운영 환경에서는 반드시 강력한 랜덤 문자열(Base64 인코딩 권장)로 교체하세요.

### 4. Database & Infrastructure (App Module)
- **위치**: `app/api/src/main/resources/application.yml`
- **설명**: 프로젝트 루트의 `docker/` 디렉토리에 정의된 인프라를 사용하는 경우 기본 설정(`localhost`)이 자동으로 적용됩니다.
- **포트 정보**:
    - MySQL: 3306
    - MongoDB: 27017
    - Redis Sentinel: 26380, 26381, 26382 (Master: 6379)
    - Kafka: 9001, 9002, 9003

---
## 실행 방법 (Local)

### 1. 인프라 컨테이너 실행
로컬 개발에 필요한 DB, Redis, Kafka 등을 실행합니다.
```bash
./infra-start.sh
```

### 2. API 서버 실행
통합된 `application.yml`을 사용하여 서버를 실행합니다.
```bash
./gradlew app:api:bootRun
```

---

# 개발 로드맵 (Development Roadmap)

## 1. 백엔드 로드맵

### Phase 1: 핵심 기반 구축 (완료)
- [x] **프로젝트 구조**: Modular Monolith 설정 (app, module, common, infra).
- [x] **인프라 설정**: MySQL, Redis, Mongo, Kafka 기본 설정 및 Docker Compose 구성.
- [x] **공통 모듈**: Security, Logger, I18n, 예외 처리(Exception handling).
- [x] **관측성(Observability)**: P6Spy SQL 로깅, 요청 로깅 설정.
- [x] **설정 최적화**: 분산된 application.yml 파일을 통합 및 정비.

### Phase 2: 인증 및 회원 (진행 중)
- [x] **인증 모듈**: Spring Security 기반 OAuth2 (Google/Apple) 통합.
- [x] **회원 모듈**: 엔티티 설계, 리포지토리 패턴 (DIP 적용), 기본 서비스 구현.
- [x] **테스트**: Auth 서비스 및 핸들러 유닛 테스트 (Kotest).
- [x] **회원 API**: 프로필 수정 및 조회 엔드포인트.
- [x] **토큰 갱신**: Redis를 이용한 JWT Refresh Token 로직.

### Phase 3: 매칭 및 채팅 (계획됨)
- [ ] **매칭 모듈**:
    - [ ] 데일리 추천 알고리즘 (Batch/Scheduler).
    - [ ] Redis 기반 실시간 랜덤 매칭 큐.
- [ ] **채팅 모듈**:
    - [ ] WebSocket (STOMP) 설정.
    - [ ] MongoDB 메시지 저장소 구현.
    - [ ] 채팅방 관리 로직.

### Phase 4: 피드 및 알림 (계획됨)
- [ ] **피드 모듈**: 타임라인, 게시글 CRUD.
- [ ] **알림 모듈**: FCM(Firebase Cloud Messaging) 연동.

---

## 2. 운영 준비 항목 (User Action Items)

**실제 운영 환경 배포를 위해 아래 항목들이 준비되어야 합니다.**

### A. 인증 관련 키 발급
- [ ] **Google OAuth**: 클라이언트 ID & Secret 발급 (GCP Console).
- [ ] **Apple Sign In**: Service ID, Key ID, Private Key(.p8) 발급 (Apple Developer).

### B. 클라우드 인프라 구성
- [ ] **운영 서버**: Oracle Cloud 또는 AWS 인스턴스 설정.
- [ ] **객체 스토리지**: AWS S3 버킷 생성 및 IAM 계정 설정.
- [ ] **푸시 알림**: Firebase 프로젝트 생성 및 서비스 계정 키 다운로드.

### C. 환경 변수 및 보안 설정
- [ ] **운영 환경 설정**: `application-production.yml`에 필요한 환경 변수(DB 패스워드, API 키 등) 정의.
- [ ] **CI/CD**: GitHub Secrets에 민감한 정보 등록 및 배포 파이프라인 구성.
