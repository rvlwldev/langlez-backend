package com.langlez.core.event.member

/**
 * 탈퇴는 `Member.withdraw()` 뿐이고 되돌리는 메서드가 없는 단방향 상태 전이다.
 * 같은 memberId 로 두 번째 탈퇴 사건이 생길 수 없으므로 [id] 하나만으로 그 발생 건을
 * 유일하게 가리킨다 (멱등 키). `MemberFollowedEvent` 가 `followId` 를 따로 싣는 것과 달리
 * 시각을 더 실을 필요가 없다 — 언팔로우 후 재팔로우처럼 같은 필드값이 반복될 여지가 없다.
 */
data class MemberWithdrawnEvent(
    val id: Long,
)
