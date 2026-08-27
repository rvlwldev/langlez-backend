package com.langlez.core

/**
 * 회원 계정 상태 조회. member 모듈이 구현한다.
 *
 * `common` 의 인증 필터가 매 요청 계정 상태를 봐야 하는데 `common` 은 `core` 에만 의존하므로
 * `module/member` 의 `Member.Status` 를 참조할 수 없다. 그래서 상태 enum 을 여기 둔다.
 *
 * `Boolean` 하나로 줄이지 않는 이유: 정지와 탈퇴는 사용자에게 다른 문구를 내야 한다
 * (`member.suspended` / `member.withdrawn`). 참·거짓만 돌려주면 그 구분이 사라진다.
 */
interface MemberStatusQuery {

    /** 회원이 없으면 null. 탈퇴 회원은 행이 남으므로 [Status.WITHDRAWN] 으로 온다. */
    fun findStatus(memberId: Long): Status?

    /**
     * `Member.Status` 의 거울.
     *
     * 구현체가 `when` 으로 하나씩 매핑하니 원본에 값이 늘면 컴파일이 깨진다.
     * 이름을 그대로 옮기지 않고 매핑을 두는 건, 원본이 바뀌었는데 여기가 안 바뀐 상태로
     * 조용히 통과하는 걸 막기 위해서다.
     */
    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN }
}
