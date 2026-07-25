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
 */
@Component
class EchoWriter(private val postRepository: PostRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveLike(postId: Long, memberId: Long): PostLike =
        postRepository.saveLike(PostLike(postId = postId, memberId = memberId))
}
