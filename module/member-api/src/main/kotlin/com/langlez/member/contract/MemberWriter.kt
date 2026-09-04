package com.langlez.member.contract

/**
 * 회원 상태를 바꾸는 운영 조치. `moderation` 이 신고를 판단해 실행한다.
 *
 * 읽기 계약([MemberReader])과 나눈다 — 조회는 화면마다 부르지만 이건 운영자가 드물게 쓰고
 * 감사 대상이다. 한 인터페이스에 섞으면 소비자가 필요 없는 권한까지 갖는다.
 *
 * [actorId] 는 조치한 운영자다. 구현이 이력에 남긴다. 호출자가 요청 본문에서 받은 값을
 * 그대로 넘기면 감사 기록이 위조되므로 인증에서 온 값이어야 한다.
 */
interface MemberWriter {

    /**
     * 회원을 정지시킨다.
     *
     * [days] 가 null 이면 무기한이다 — 무기한 정지는 만료 배치가 풀지 않으니 사람이 해제해야 한다.
     *
     * 아래 셋은 **구현이 거부한다.** 호출자마다 검사를 두면 새 소비자가 하나만 빠뜨려도
     * 그 경로가 열린다. 회원의 역할·상태를 아는 것도 구현 쪽이다.
     * - 자기 자신([memberId] == [actorId]) — 400
     * - 대상이 운영자 — 403. 운영자끼리 서로 잠그면 복구가 DB 직접 수정뿐이다
     * - 이미 탈퇴한 회원 — 400
     */
    fun suspend(memberId: Long, reason: String?, days: Long?, actorId: Long)

    /** 정지를 푼다. 정지 상태가 아닌 회원이면 거부한다. */
    fun unsuspend(memberId: Long, actorId: Long)
}
