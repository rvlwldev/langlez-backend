package com.langlez.rdb.outbox

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

@NoRepositoryBean
interface OutBoxRepository<T : OutBox> : JpaRepository<T, Long> {
    fun fetch(chunk: Int, maxTries: Int): List<T> = findPendingOrderByCreatedAtAsc(
        maxRetries = maxTries,
        limit = PageRequest.of(0, chunk)
    )

    fun fetchProcessed(chunk: Int): List<T> = findAllByCompletedAtIsNotNullOrFailedAtIsNotNull(PageRequest.of(0, chunk))

    /**
     * 행 잠금으로 선점하고, 남이 이미 잡은 행은 기다리지 말고 건너뛴다(SKIP LOCKED).
     *
     * **`@Transactional` 이 반드시 있어야 한다.** Spring Data 의 기본 트랜잭션은 `SimpleJpaRepository`
     * 가 구현하는 CRUD 메서드에만 붙고 파생 쿼리·`@Query` 에는 안 붙는다. 트랜잭션 없이 잠금 쿼리를 쏘면
     * 하이버네이트가 `Query requires transaction be in progress` 로 매번 터진다 —
     * 아웃박스 발행이 통째로 멈추는데 스케줄러 로그를 보기 전에는 드러나지 않는다.
     *
     * 주의: 이 잠금만으로는 중복 발행을 막지 못한다. 여기서 연 트랜잭션이 fetch 직후 커밋되면서
     * 잠금이 즉시 풀리기 때문이다. **중복 발행을 실제로 막는 건 하위 스케줄러의 `@DistributedLock` 이다.**
     * `OutBoxProcessor` 를 상속하는 스케줄러는 `@DistributedLock` 을 반드시 붙여야 한다.
     *
     * ### `status` 를 파라미터가 아니라 JPQL 상수로 박은 이유
     *
     * V17 의 `IDX_*_EVENT_OUTBOX_PENDING` 은 `where status = 'PENDING'` **부분 인덱스**다.
     * 플래너는 술어를 증명해야 부분 인덱스를 쓰는데, `status` 가 바인드 파라미터로 나가면
     * generic plan 에서 `status = $1` 이 `status = 'PENDING'` 을 함의한다고 증명하지 못한다.
     *
     * 이건 이론이 아니라 이 쿼리가 정확히 걸리는 함정이다. 2초마다 도는 폴러는 pgjdbc 의
     * `prepareThreshold`(기본 5)를 즉시 넘겨 서버사이드 PREPARE 로 전환된다. 아카이브 직후(06:00)나
     * 앱 재시작 직후처럼 테이블이 거의 빈 시점에 그 전환이 일어나면 Seq Scan 추정 비용이 낮아
     * 플래너가 **generic plan 을 영구 채택**하고, 그 커넥션이 닫힐 때까지 custom plan 으로 안 돌아온다.
     * 그 뒤 낮 동안 수만 건이 쌓여도 2초마다 전량 Seq Scan 을 한다 — A-04 결함이 그대로 재현된다.
     *
     * **파생 쿼리로는 이걸 못 막는다.** 메서드 시그니처의 기본값(`Status.PENDING`)은 Kotlin 이
     * 호출 측에서 채우는 값일 뿐, Hibernate 에게는 그냥 바인드 인자다.
     * JPQL 에 상수로 박아야 네이티브 SQL 에도 리터럴로 나가고 generic plan 에서도 안전하다.
     *
     * `tries` 는 파라미터로 둬도 된다. 부분 인덱스 술어가 아니라 인덱스 밖 필터라
     * 값을 몰라도 계획 선택이 달라지지 않는다.
     *
     * `#{#entityName}` 은 `@NoRepositoryBean` 제네릭 베이스에서 구현 리포지토리마다
     * 자기 엔티티 이름으로 치환된다 (`MemberOutBox`, `ChatOutBox`, ...).
     */
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")) // -2 = SKIP LOCKED
    @Query(
        """
        select o from #{#entityName} o
         where o.status = com.langlez.rdb.outbox.OutBox.Status.PENDING
           and o.tries <= :maxRetries
         order by o.createdAt asc
        """
    )
    fun findPendingOrderByCreatedAtAsc(
        @Param("maxRetries") maxRetries: Int,
        limit: Pageable = PageRequest.of(0, Int.MAX_VALUE)
    ): List<T>

    fun findAllByCompletedAtIsNotNullOrFailedAtIsNotNull(page: Pageable): List<T>
}
