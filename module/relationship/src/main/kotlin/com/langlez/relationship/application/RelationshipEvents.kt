package com.langlez.relationship.application

/**
 * 팔로우 도메인 이벤트.
 *
 * 받는 쪽(알림 등)은 카프카 JSON 을 자기 타입으로 읽으므로 클래스 위치는 계약이 아니다.
 * 다른 모듈이 이 타입 자체를 참조해야 할 때 `core/event/relationship/` 으로 옮긴다.
 *
 * 언팔로우·차단 이벤트는 두지 않았다. 지금 그걸 소비할 모듈이 없고,
 * 차단 판정은 `core.BlockQuery` 로 그때그때 조회하는 쪽이 복제본보다 정확하다.
 */
data class MemberFollowedEvent(val followerId: Long, val followedId: Long)
