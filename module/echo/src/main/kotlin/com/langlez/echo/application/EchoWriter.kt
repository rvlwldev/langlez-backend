package com.langlez.echo.application

import com.langlez.echo.domain.PostLike
import com.langlez.echo.domain.PostRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 유니크 제약 위반 가능성이 있는 insert를 REQUIRES_NEW로 별도 물리 트랜잭션에서 실행한다.
 *
 * likePost의 트랜잭션 안에서 바로 저장을 시도하면, 유니크 제약 위반 발생 시 그 트랜잭션
 * 전체가 rollback-only로 마킹돼 이후 catch로 흡수해도 커밋 시점에
 * UnexpectedRollbackException이 던져진다. 같은 클래스 self-invocation은 프록시를
 * 안 거치므로 별도 빈으로 분리했다.
 *
 * **트레이드오프**: saveLike는 REQUIRES_NEW로 즉시 커밋되고, 그 뒤 호출자(EchoService)가
 * 하는 incrementLikeCount는 호출자 자신의 트랜잭션에서 나중에 커밋된다 — 더 이상 원자적이지
 * 않다. incrementLikeCount 실패 시 PostLike row는 남고 카운트는 반영 안 될 수 있다(단,
 * decrementLikeCount는 `likeCount > 0` 가드가 있어 음수로는 안 간다). 단순 UPDATE라 실패 경로가
 * 거의 없어 DB 단절·크래시 같은 예외 상황에서만 발생하고, 막으려는 "동시 중복 좋아요"보다
 * 훨씬 드물다고 보고 받아들인 트레이드오프다. RelationshipWriter와 동일한 판단.
 */
@Component
class EchoWriter(private val postRepository: PostRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveLike(postId: Long, memberId: Long): PostLike =
        postRepository.saveLike(PostLike(postId = postId, memberId = memberId))
}
