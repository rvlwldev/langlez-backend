package com.langlez.core.event.echo

/**
 * 피드 도메인 이벤트.
 *
 * echo 는 notification 을 직접 부르지 않는다. 이 이벤트가 아웃박스를 거쳐 카프카로 나가고,
 * 받는 모듈이 알림 발송을 한다.
 *
 * 글 작성 자체는 이벤트로 내보내지 않는다 — 받을 쪽이 없다. 알림거리가 되는 건 남이 내 글에
 * 반응했을 때뿐이라 좋아요·댓글 둘만 둔다.
 */
data class EchoPostLikedEvent(
    val postId: Long,
    val authorId: Long,
    val likerId: Long,
)

data class EchoCommentCreatedEvent(
    val postId: Long,
    val authorId: Long,
    val commentId: Long,
    val commenterId: Long,
    val preview: String,
)
