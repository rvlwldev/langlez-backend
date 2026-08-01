# 📋 Project Refactoring & Feature Roadmap (TODO.md)

이 문서는 `refactoring` 브랜치의 코드 리뷰 및 진단 결과를 바탕으로 향후 고도화해야 할 비즈니스 기능 및 아키텍처 작업 항목들을 정리한 로드맵입니다.

---

## 👤 1. Member (회원) 모듈 비즈니스 고도화

- [ ] **회원 상태 라이프사이클 (`Member.status`) 도입**
  - [ ] `Status` Enum 정의: `ACTIVE` (정상), `SUSPENDED` (정지), `WITHDRAWN` (탈퇴)
  - [ ] `Member` 엔티티에 `status` 필드 추가 및 데이터베이스 인덱싱
  - [ ] 정지/탈퇴 회원의 서비스 로그인 및 주요 API 접근 차단 검증 필터 연동

- [ ] **회원 탈퇴 (Withdraw) & 개인정보 비식별화 (Soft Delete)**
  - [ ] `MemberService.withdraw(memberId)` 회원 탈퇴 처리 구현
  - [ ] 30일 유예 기간 정책 수립 및 개인정보 익명화(`deleted_xxx`) 스케줄러 처리
  - [ ] `MemberWithdrawnEvent` 도메인 이벤트 발행 및 타 모듈 연관 데이터 연쇄 처리

- [ ] **프로필 사진 (Profile Image URL) 필드 추가**
  - [ ] `Member.profileImageUrl` 엔티티 필드 및 기본 프로필 이미지 처리 로직 추가
  - [ ] 프로필 이미지 변경 및 삭제 서비스 메서드 구현

- [ ] **약관 동의 이력 관리 (Terms of Service & Privacy Policy)**
  - [ ] 서비스 이용약관 동의 일시(`agreedTermsAt`) 필드 추가
  - [ ] 마케팅 정보 수신 동의 여부 및 동의 일시(`agreedMarketingAt`) 필드 추가

- [ ] **마이페이지 통합 조회 (`getMe`) & 회원 페이징 검색 API**
  - [ ] 현재 로그인한 본인 정보(프로필, 역할, 가입일, 상태) 통합 DTO 조회 API 구현
  - [ ] 닉네임/유저네임 기반 회원 페이징 검색 Query API 구현

---

## 🔐 2. Auth (인증/인가) 모듈 보안 및 세션 고도화

- [ ] **회원 탈퇴 시 전체 토큰 즉시 무효화 (Token Revoke All)**
  - [ ] 회원 탈퇴 이벤트 발생 시 해당 회원의 모든 Refresh Token 레디스 즉시 삭제
  - [ ] 잔여 Access Token 전체를 Blacklist에 등록하여 즉시 차단

- [ ] **Refresh Token Rotation (RTR) 토큰 탈취 감지 & 자동 차단**
  - [ ] 무효화된 이전 Refresh Token으로 재발행(Refresh) 시도 감지
  - [ ] 탈취 시도 감지 시 해당 회원의 모든 세션 토큰 즉시 강제 파기(Revoke All)

- [ ] **멀티 디바이스 세션 관리 & 원격 로그아웃 (Active Devices)**
  - [ ] Refresh Token 키 구조 변경: `refresh_token:{memberId}:{deviceId}`
  - [ ] 현재 접속 중인 기기 목록 조회 (IP, OS, 디바이스명, 마지막 활동 시간)
  - [ ] 특정 기기 선택 원격 로그아웃 API 구현

- [ ] **소셜 계정 추가 연동 / 연동 해제 (OAuth Account Linking/Unlinking)**
  - [ ] Google / Apple 소셜 계정 교차 연동 및 연동 해제 서비스 구현

---

## 🏗️ 3. Infra & Core 메시징 및 아키텍처 고도화

- [ ] **Member 모듈 History 아카이버 등록 (`MemberOutBoxArchiveScheduler`)**
  - [ ] `MemberOutBoxArchiveScheduler` 작성하여 `member_event_outbox_history` 데이터 무한 증가 방지
  - [ ] `@Scheduled` + `@DistributedLock`으로 주기적 배치 아카이빙 처리

- [ ] **Redis Stream 독극물 메시지 처리용 DLQ (Dead Letter Queue)**
  - [ ] PEL 복구(`autoClaim`) 재처리 횟수(Retry Count) 추적 로직 추가
  - [ ] N회 이상 연속 실패 시 `dlq:stream`으로 메시지 이관 및 예외 알림 처리

- [ ] **컨슈머 멱등성 (Idempotency) & 중복 수신 방지 하네스**
  - [ ] `@Idempotent` 어노테이션 및 AOP 인터셉터 작성
  - [ ] Redis `SETNX` 기반 `messageId` 중복 검사로 At-Least-Once 다중 수신 사이드이펙트 차단

- [ ] **Global Exception Response 포맷 고도화**
  - [ ] `ExceptionResponse`에 `code`, `timestamp`, `path`, `traceId` (MDC) 포함
  - [ ] Distributed Tracing 지원을 위한 Web Filter MDC TraceId 주입 연동
