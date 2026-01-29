# 외부 서비스 연동 및 인프라 설정 가이드 (Pre-requisites)

이 문서는 프로젝트 개발 전, 개발자(사용자)가 미리 수행해야 할 외부 서비스(OAuth, FCM, S3)의 계정 설정 및 키 발급 절차를 상세히 안내합니다.
설정이 완료되면 발급받은 키(Key) 값들을 안전한 곳(로컬 환경 변수 등)에 보관해 주시기 바랍니다.

---

## 1. Google OAuth 2.0 (소셜 로그인)

구글 계정을 이용한 사용자 인증을 위해 필요합니다.

*   **설정 사이트**: [Google Cloud Console](https://console.cloud.google.com/)

### 📝 설정 단계 (Step-by-Step)
1.  **프로젝트 생성**:
    - 콘솔 접속 후 상단 프로젝트 선택 드롭다운 > `새 프로젝트` 클릭.
    - 프로젝트 이름 입력(예: `Langlez-Auth`) 후 `만들기`.
2.  **OAuth 동의 화면 설정**:
    - 왼쪽 메뉴 `API 및 서비스` > `OAuth 동의 화면` 클릭.
    - `User Type`: **외부(External)** 선택 후 `만들기`.
    - **앱 정보**: 앱 이름(Langlez), 사용자 지원 이메일 입력.
    - **개발자 연락처**: 이메일 입력 후 `저장 후 계속`.
    - **범위(Scopes)**: `범위 추가 또는 삭제` 클릭 > `auth/userinfo.email`, `auth/userinfo.profile` 체크 후 업데이트.
3.  **클라이언트 ID 발급**:
    - 왼쪽 메뉴 `사용자 인증 정보` > `+ 사용자 인증 정보 만들기` > `OAuth 클라이언트 ID` 클릭.
    - `애플리케이션 유형`: **웹 애플리케이션** 선택.
    - `승인된 리다이렉트 URI`: 개발 서버 주소 입력 (예: `http://localhost:8080/login/oauth2/code/google` 등 Spring Security 기본 경로 또는 커스텀 경로).
    - `만들기` 클릭.

### 🔑 보관해야 할 키 (Secrets)
*   **클라이언트 ID** (Client ID)
*   **클라이언트 보안 비밀번호** (Client Secret) - **외부 유출 절대 금지**

---

## 2. Apple Sign In (애플 로그인)

iOS 앱 심사 필수 항목이며, 웹/안드로이드에서도 연동 가능합니다. (Apple Developer 멤버십 필요)

*   **설정 사이트**: [Apple Developer Center](https://developer.apple.com/account/)

### 📝 설정 단계 (Step-by-Step)
1.  **App ID 생성**:
    - `Certificates, Identifiers & Profiles` > `Identifiers` > `+` 버튼.
    - `App IDs` 선택 > `App` 선택.
    - `Bundle ID` 입력 (예: `com.langlez.app`).
    - **Capabilities**에서 `Sign In with Apple` 체크.
2.  **Service ID 생성** (웹/백엔드 연동용):
    - `Identifiers` > `+` > `Service IDs` 선택.
    - Identifier 입력 (예: `com.langlez.app.service`).
    - 생성 후 해당 ID 클릭 > `Sign In with Apple` 체크 > `Configure`.
    - `Primary App ID`에 위에서 만든 App ID 연결.
    - `Domains` 및 `Return URLs`에 서버 도메인/콜백 주소 입력.
3.  **Key (.p8) 발급**:
    - `Keys` 메뉴 > `+` 버튼.
    - Key Name 입력 > `Sign in with Apple` 체크 > `Configure` (App ID 연결).
    - `Register` 후 **.p8 파일 다운로드** (재다운로드 불가하므로 백업 필수).

### 🔑 보관해야 할 키 (Secrets)
*   **Team ID** (멤버십 정보에서 확인)
*   **Key ID** (키 생성 시 확인)
*   **Client ID** (Service ID)
*   **Private Key 파일** (`AuthKey_XXXXXXXX.p8`)

---

## 3. FCM (Firebase Cloud Messaging)

앱 푸시 알림 발송을 위한 표준 서비스입니다.

*   **설정 사이트**: [Firebase Console](https://console.firebase.google.com/)

### 📝 설정 단계 (Step-by-Step)
1.  **프로젝트 생성**:
    - `프로젝트 추가` > 이름 입력(`Langlez`) > `계속`.
2.  **서비스 계정 키 발급**:
    - 프로젝트 개요 옆 `톱니바퀴` > `프로젝트 설정`.
    - `서비스 계정` 탭 클릭.
    - `Firebase Admin SDK` 영역에서 `새 비공개 키 생성` 클릭.
    - JSON 파일이 다운로드됩니다.

### 🔑 보관해야 할 키 (Secrets)
*   **서비스 계정 키 파일** (`service-account-file.json`) - **서버에 저장하여 사용**

---

## 4. AWS S3 (파일 저장소)

프로필 이미지, 채팅 미디어 등을 저장하는 클라우드 스토리지입니다.

*   **설정 사이트**: [AWS Management Console](https://console.aws.amazon.com/s3/)

### 📝 설정 단계 (Step-by-Step)
1.  **버킷 생성**:
    - S3 서비스 > `버킷 만들기`.
    - `버킷 이름` 입력 (전역 유일해야 함, 예: `langlez-media-prod`).
    - `AWS 리전` 선택 (예: `ap-northeast-2` 서울).
    - **퍼블릭 액세스 차단 설정**: '모든 퍼블릭 액세스 차단' 체크 (보안 권장).
2.  **IAM 사용자 생성 및 권한 부여**:
    - IAM 서비스 > `사용자` > `사용자 생성`.
    - 이름 입력(`s3-uploader`) > `직접 정책 연결`.
    - 권한 정책에서 `AmazonS3FullAccess` 검색 후 체크 (또는 특정 버킷만 허용하는 커스텀 정책 권장).
3.  **액세스 키 발급**:
    - 생성된 사용자 클릭 > `보안 자격 증명` 탭.
    - `액세스 키 만들기` > `Application running outside AWS` 선택.
    - 생성된 키 복사.

### 🔑 보관해야 할 키 (Secrets)
*   **Access Key ID**
*   **Secret Access Key** - **생성 시에만 확인 가능**
*   **Bucket Name**
*   **Region**

---

## 5. Oracle Cloud Server (운영 서버)

이 서비스는 **Oracle Cloud Always Free (ARM 인스턴스)**를 메인 서버로 사용할 예정입니다.
2026년 기준, 계정 정지나 인스턴스 회수 이슈를 피하기 위한 **안정적 발급 및 운영 가이드**입니다.

### ⚠️ 핵심 전략 (Anti-Reclaim)
오라클은 무료 계정의 자원 낭비를 막기 위해 **유휴 인스턴스(CPU/메모리 사용량 20% 미만)를 강제로 회수**합니다.
이를 방지하는 가장 확실한 방법은 계정을 **Pay-As-You-Go(종량제)**로 업그레이드하는 것입니다.
*   **비용**: Always Free 한도(4 OCPU, 24GB RAM) 내에서 사용하면 **청구 금액은 0원**입니다.
*   **효과**: 유료 계정으로 취급되어 유휴 상태라도 인스턴스가 회수되지 않습니다.

### 📝 설정 단계 (Step-by-Step)
1.  **회원 가입 및 리전 선택**:
    - [Oracle Cloud Free Tier](https://www.oracle.com/cloud/free/) 접속.
    - **Home Region 선택 (중요)**: 평생 변경 불가. ARM 재고가 비교적 안정적인 **US West (San Jose)** 또는 **US West (Phoenix)** 추천. (서울/춘천은 재고 부족 빈번)
    - **카드 등록**: 해외 결제가 가능한 **신용카드(Credit Card)** 사용 권장 (체크카드는 거절 확률 높음). 주소는 카드 청구지 주소와 영문 철자까지 정확히 일치시킬 것.
2.  **인스턴스 생성**:
    - 로그인 후 `Compute` > `Instances` > `Create Instance`.
    - **Image**: `Oracle Linux` 또는 `Ubuntu` 선택.
    - **Shape**: `Ampere` > `VM.Standard.A1.Flex` 선택.
    - **Configure**: OCPU `4개`, Memory `24GB`로 설정 (무료 한도 최대치).
    - **SSH Key**: `Generate a key pair`로 키 다운로드 또는 본인의 Public Key 업로드 (분실 시 접속 불가).
3.  **Pay-As-You-Go 업그레이드 (필수)**:
    - 콘솔 상단 배너의 `Upgrade` 또는 `Billing` 메뉴 진입.
    - `Pay As You Go` 플랜 선택 후 결제 수단 재확인.
    - 약 100달러(약 13만원) 가결제 후 즉시 취소됨 (한도 확인 필요).
4.  **과금 방지 설정 (Budgets)**:
    - `Billing & Cost Management` > `Budgets`.
    - `Create Budget` > 월 예산 `1,000원(또는 $1)` 설정.
    - 알림 규칙(Alert Rule) 추가: 예산의 80~100% 도달 시 이메일 알림.

### 🔑 보관해야 할 키 (Secrets)
*   **SSH Private Key** (`.key` 또는 `.pem`) - **서버 접속용, 분실 절대 금지**
*   **Public IP Address** (인스턴스 생성 후 확인)

---

### ⚠️ 보안 주의사항
위에서 발급받은 모든 **Secret Key**, **Private Key(.p8)**, **JSON 파일**은 절대 GitHub 등 공개된 저장소에 커밋하지 마십시오.
`.gitignore`에 등록되어 있는지 확인하고, 로컬 환경 변수나 보안 저장소(AWS Secrets Manager, GitHub Secrets)를 통해 관리해야 합니다.
