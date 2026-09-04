package com.langlez.member.application

import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class MemberSuspenderTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val suspendRepo = mockk<MemberSuspendHistoryRepository>(relaxed = true)
    val suspender = MemberSuspender(repo, suspendRepo)

    afterEach { clearMocks(repo, suspendRepo, answers = false) }

    fun member(id: Long = 1L, status: Member.Status = Member.Status.ACTIVE) = Member(
        id = id,
        email = "user$id@test.com",
        handle = "user$id",
        status = status,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
    )

    Given("회원 정지 시") {

        When("활성 회원을 기간 없이 정지하면") {
            every { repo.find(1L) } returns member()
            every { repo.save(any()) } answers { firstArg() }

            val saved = slot<MemberSuspendHistory>()
            every { suspendRepo.save(capture(saved)) } answers { firstArg() }

            suspender.suspend(1L, reason = "policy violation", days = null, actorId = 7L)

            Then("상태가 SUSPENDED 로 바뀌어 저장된다") {
                verify { repo.save(match { it.status == Member.Status.SUSPENDED }) }
            }

            Then("이력에 사유와 조치자가 남는다") {
                saved.captured.reason shouldBe "policy violation"
                saved.captured.actorId shouldBe 7L
            }

            // releaseAt 이 null 이면 만료 배치가 집지 않는다. 무기한 정지는 사람이 풀어야 한다.
            Then("기간이 없으면 releaseAt 이 비어 있다") {
                saved.captured.releaseAt shouldBe null
            }
        }

        When("기간을 정해 정지하면") {
            every { repo.find(1L) } returns member()
            every { repo.save(any()) } answers { firstArg() }

            val saved = slot<MemberSuspendHistory>()
            every { suspendRepo.save(capture(saved)) } answers { firstArg() }

            suspender.suspend(1L, reason = "spam", days = 7L, actorId = 7L)

            Then("releaseAt 이 채워진다 (이 값이 없으면 만료 배치가 영영 못 푼다)") {
                saved.captured.releaseAt.shouldNotBeNull()
            }
        }

        When("이미 탈퇴한 회원을 정지하려 하면") {
            every { repo.find(2L) } returns member(id = 2L, status = Member.Status.WITHDRAWN)

            Then("400 이고 이력도 남기지 않는다") {
                val ex = shouldThrow<LanglezException> {
                    suspender.suspend(2L, reason = null, days = null, actorId = 7L)
                }

                ex.status.value() shouldBe 400
                verify(exactly = 0) { suspendRepo.save(any()) }
            }
        }

        When("존재하지 않는 회원을 정지하려 하면") {
            every { repo.find(999L) } returns null

            Then("404 다") {
                val ex = shouldThrow<LanglezException> {
                    suspender.suspend(999L, reason = null, days = null, actorId = 7L)
                }

                ex.status.value() shouldBe 404
            }
        }
    }

    Given("정지 해제 시") {

        When("정지된 회원을 풀면") {
            every { repo.find(1L) } returns member(status = Member.Status.SUSPENDED)
            every { repo.save(any()) } answers { firstArg() }

            val open = listOf(
                MemberSuspendHistory(memberId = 1L, reason = "old", releaseAt = null),
                MemberSuspendHistory(memberId = 1L, reason = "new", releaseAt = null),
            )
            every { suspendRepo.findOpen(1L) } returns open

            suspender.unsuspend(1L, actorId = 7L)

            /**
             * 두 verify 를 한 Then 에 둔다. afterEach 의 clearMocks 가 Then 사이에 돌아
             * 호출 기록을 지우므로, 두 번째 Then 의 verify 는 빈 기록을 보게 된다.
             */
            Then("상태가 ACTIVE 로 저장되고 열려 있던 이력이 함께 저장된다") {
                verify { repo.save(match { it.status == Member.Status.ACTIVE }) }
                verify { suspendRepo.saveAll(open) }
            }

            /**
             * 이력을 안 닫으면 isReleased 가 영영 false 로 남아
             * MemberSuspendReleaseScheduler 가 이미 풀린 회원을 매 주기 다시 잡는다.
             */
            Then("열려 있던 이력이 전부 닫힌다") {
                open.all { it.isReleased } shouldBe true
            }
        }

        When("정지 상태가 아닌 회원을 풀려 하면") {
            every { repo.find(1L) } returns member(status = Member.Status.ACTIVE)

            Then("400 이고 이력을 건드리지 않는다") {
                val ex = shouldThrow<LanglezException> { suspender.unsuspend(1L, actorId = 7L) }

                ex.status.value() shouldBe 400
                verify(exactly = 0) { suspendRepo.saveAll(any()) }
            }
        }

        When("열린 이력이 없으면") {
            every { repo.find(1L) } returns member(status = Member.Status.SUSPENDED)
            every { repo.save(any()) } answers { firstArg() }
            every { suspendRepo.findOpen(1L) } returns emptyList()

            suspender.unsuspend(1L, actorId = 7L)

            Then("빈 저장을 부르지 않는다") {
                verify(exactly = 0) { suspendRepo.saveAll(any()) }
            }
        }
    }
})
