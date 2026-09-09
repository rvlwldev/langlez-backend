package com.langlez.profile.application

import com.langlez.profile.domain.ProfileRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class VisitCountSyncSchedulerTest : BehaviorSpec({

    val repo = mockk<ProfileRepository>(relaxed = true)

    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val scheduler = VisitCountSyncScheduler(repo, tx)

    afterEach { clearMocks(repo, answers = false) }

    Given("플러시 대상 방문자 수가 있을 때") {

        When("동기화를 돌리면") {
            every { repo.beginVisitCountFlush() } returns mapOf(9L to 3L, 10L to 7L)

            scheduler.syncVisitCounts()

            Then("각 회원의 방문 카운트를 DB에 반영하고 커밋한다") {
                verify(exactly = 1) { repo.incrementVisitCountInDb(9L, 3L) }
                verify(exactly = 1) { repo.incrementVisitCountInDb(10L, 7L) }
                verify(exactly = 1) { repo.commitVisitCountFlush(setOf(9L, 10L)) }
            }
        }
    }

    Given("플러시 대상 방문자 수가 없을 때") {

        When("동기화를 돌리면") {
            every { repo.beginVisitCountFlush() } returns emptyMap()

            scheduler.syncVisitCounts()

            Then("DB 증가를 호출하지 않는다") {
                verify(exactly = 0) { repo.incrementVisitCountInDb(any(), any()) }
            }
        }
    }
})
