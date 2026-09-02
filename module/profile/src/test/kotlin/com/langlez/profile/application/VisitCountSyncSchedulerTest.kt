package com.langlez.profile.application

import com.langlez.member.contract.MemberQuery
import com.langlez.profile.domain.ProfileRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

/**
 * handle → 회원 id 변환이 저장소에서 스케줄러로 올라왔다.
 * 그 변환은 `MemberQuery` 포트라 트랜잭션 밖에서 끝나야 하고, 없는 handle 은 조용히 빠져야 한다.
 */
class VisitCountSyncSchedulerTest : BehaviorSpec({

    val repo = mockk<ProfileRepository>(relaxed = true)
    val members = mockk<MemberQuery>()

    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val scheduler = VisitCountSyncScheduler(repo, members, tx)

    Given("플러시 대상에 이미 지워진 handle 이 섞여 있으면") {

        When("동기화를 돌리면") {
            every { repo.beginVisitCountFlush() } returns mapOf("alive" to 3L, "ghost" to 7L)
            every { members.findIdByHandle("alive") } returns 9L
            every { members.findIdByHandle("ghost") } returns null

            scheduler.syncVisitCounts()

            Then("살아 있는 회원만 DB 에 반영한다") {
                verify(exactly = 1) { repo.incrementVisitCountInDb(9L, 3L) }
            }

            Then("한 건 때문에 나머지가 롤백되지 않게 없는 handle 은 그냥 건너뛴다") {
                verify(exactly = 0) { repo.incrementVisitCountInDb(any(), 7L) }
            }

            Then("반영 못 한 handle 도 dirty 셋에서 함께 정리한다 (안 하면 영영 쌓인다)") {
                verify(exactly = 1) { repo.commitVisitCountFlush(setOf("alive", "ghost")) }
            }
        }
    }
})
