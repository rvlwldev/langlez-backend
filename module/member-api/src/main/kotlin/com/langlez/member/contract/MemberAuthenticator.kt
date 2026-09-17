package com.langlez.member.contract

/**
 * 소셜 로그인 진입점. auth 가 로그인·토큰 갱신 때 회원을 조회·생성·상태검사하기 위해 부른다.
 *
 * **조회·생성·상태검사를 한 메서드로 묶는 이유**: 예전엔 로그인 한 번에 member 를 최대 3번
 * 불렀다(`findByProvider` → `findByEmail` → `createMember`). 이 포트는 곧 gRPC 로 대체되므로
 * 그러면 왕복이 셋이 된다. [MemberReader] 의 KDoc 이 같은 이유로 필드별 분리를 금지하고 있다.
 *
 * **이메일 중복 검사를 구현 쪽으로 두는 이유**: 조회 후 생성을 호출자가 하면 그 사이에 TOCTOU
 * 창이 생기고, 유니크 제약과 `@Retryable` 은 이미 member 쪽에 있다. 판정과 저장이 같은 쪽에
 * 있어야 한다.
 *
 * **`provider` 와 [AccountInfo.role] 이 `String` 인 이유**: `Member.Provider` / `Member.Role`
 * enum 을 계약으로 올리면 회원 도메인의 열거값이 전 모듈 공용이 된다. [MemberReader] 가
 * `gender` 를 String 으로 내는 것과 같은 이유다. 모르는 provider 문자열은 구현이 400 으로 거부한다.
 *
 * **`Reader`/`Writer` 네이밍에서 벗어나는 이유**: [MemberWriter] 는 "운영자가 드물게 쓰고
 * 감사 대상"이라 명시하고 읽기와 일부러 분리해 뒀다. 거기에 회원 생성을 넣으면 moderation 이
 * 생성 권한까지 갖는다.
 */
interface MemberAuthenticator {

    /**
     * 소셜 로그인 진입. 없으면 만들고 있으면 찾는다.
     *
     * 정지·탈퇴 회원은 `LanglezException(FORBIDDEN, e.message, e)` 를 던져 거부한다 — 사유
     * (`member.suspended`/`member.withdrawn`)를 boolean 이나 null 로 뭉개면 사용자에게 다른
     * 문구를 낼 수 없다([MemberReader.findStatus] 의 KDoc 과 같은 이유). 이메일이 다른 계정에
     * 이미 쓰인 경우도 구현이 거부한다.
     */
    fun authenticate(provider: String, providerId: String, email: String?, displayName: String?): AccountInfo

    /**
     * 리프레시용.
     *
     * **`null` 은 회원 부재만 뜻한다.** 정지·탈퇴는 `null` 이 아니라 [authenticate] 와 같은 규약으로
     * `LanglezException(FORBIDDEN, e.message, e)` 를 던진다 — 사유를 잃으면 안 되기 때문이다
     * ([MemberReader.findStatus] 의 KDoc 과 같은 이유). 회원 부재와 비활성 상태를 함께 `null` 로
     * 합치면 호출자가 401(재로그인 유도)과 403(정지/탈퇴 안내)을 구분할 방법이 없어진다.
     */
    fun findLoginable(memberId: Long): AccountInfo?

    data class AccountInfo(val id: Long, val handle: String, val role: String)
}
