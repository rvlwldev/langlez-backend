package com.langlez.relationship.application

import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * follow/block insert를 REQUIRES_NEW로 별도 물리 트랜잭션에서 실행한다.
 *
 * RelationshipService의 트랜잭션 안에서 바로 저장을 시도하면, 유니크 제약 위반이
 * 발생한 순간 그 트랜잭션 전체가 rollback-only로 마킹된다. 이후 서비스가
 * DataIntegrityViolationException을 catch해서 정상 반환해도, 커밋 시점에 Spring이
 * rollback-only를 감지해 UnexpectedRollbackException을 던진다 — catch로 막을 수
 * 없는 위치에서 실패한다. REQUIRES_NEW로 격리하면 실패가 이 트랜잭션 안에서 끝나고,
 * 호출자(RelationshipService)는 자신의 트랜잭션을 훼손하지 않고 예외를 잡을 수 있다.
 *
 * 같은 클래스 내부 self-invocation은 프록시를 안 거치므로 별도 빈으로 분리했다
 * (@DistributedLock과 동일한 이유).
 */
@Component
class RelationshipWriter(private val repo: RelationshipRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveFollow(followerId: Long, followingId: Long): Follow =
        repo.saveFollow(Follow(followerId, followingId))

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveBlock(blockerId: Long, blockedId: Long): Block =
        repo.saveBlock(Block(blockerId, blockedId))
}
