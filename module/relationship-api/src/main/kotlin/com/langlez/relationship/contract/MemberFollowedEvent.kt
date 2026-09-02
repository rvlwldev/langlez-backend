package com.langlez.relationship.contract

/**
 * 팔로우 도메인 이벤트.
 *
 * notification 이 이 타입을 직접 참조하면서 모듈 간 공유 계약이 됐다. relationship 안에 두면
 * 소비하는 쪽이 relationship 모듈 전체에 의존하게 된다.
 *
 * [followId] 는 팔로우 행 id 다. **컨슈머 멱등성의 식별자라 빼면 안 된다.**
 * (followerId, followedId) 만으로는 언팔로우 후 재팔로우가 이전 이벤트와 완전히 같은 페이로드가 되어
 * 중복 배달과 구분되지 않는다. 행 id 는 재팔로우 때 새로 발급되므로 둘을 갈라준다.
 *
 * 언팔로우·차단 이벤트는 두지 않았다. 지금 그걸 소비할 모듈이 없고,
 * 차단 판정은 `BlockReader` 로 그때그때 조회하는 쪽이 복제본보다 정확하다.
 */
data class MemberFollowedEvent(val followId: Long, val followerId: Long, val followedId: Long)
