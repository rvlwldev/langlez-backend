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
 *
 * **트레이드오프**: REQUIRES_NEW는 이 insert를 별도 물리 트랜잭션으로 즉시 커밋한다. 그 뒤
 * 호출자(RelationshipService)가 하는 outbox 이벤트 저장(block의 경우 양방향 unfollow까지)은
 * 호출자 자신의 트랜잭션에서 나중에 커밋되므로, 이 둘은 더 이상 원자적이지 않다. insert가
 * 커밋된 후 호출자 트랜잭션이 어떤 이유로든 실패하면 relationship row는 남고 outbox 이벤트는
 * 사라질 수 있다. 다만 이 사이에 실패할 수 있는 건 단순 INSERT/UPDATE라 흔한 실패 경로(유니크
 * 위반 등)가 없고, DB 커넥션 단절·프로세스 크래시 같은 예외적 상황에서만 발생한다 — 이 클래스가
 * 막으려는 "동시 중복 요청"(일상적으로 발생)보다 훨씬 드물다고 판단해 받아들인 트레이드오프다.
 * PROPAGATION_NESTED(세이브포인트)가 대안이 될 수 있으나, 이 프로젝트의 JpaTransactionManager는
 * nestedTransactionAllowed가 기본값(false)이라 별도 설정 없이는 바로 못 쓴다 — 필요해지면
 * 별도로 검증 후 전환할 것.
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
