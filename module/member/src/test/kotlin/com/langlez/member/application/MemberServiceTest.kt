package com.langlez.member.application

import com.langlez.core.OnlineTracker
import com.langlez.core.Storage
import com.langlez.core.event.member.MemberWithdrawnEvent
import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class MemberServiceTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val creator = mockk<MemberCreator>()
    val tracker = mockk<OnlineTracker>()
    val storage = mockk<Storage>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val suspendHistoryRepo = mockk<MemberSuspendHistoryRepository>()

    // updateProfileUrl 이 TransactionTemplate 으로 읽기+쓰기를 묶는다. 테스트에선 그대로 실행시킨다.
    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val service = MemberService(repo, creator, tracker, storage, publisher, suspendHistoryRepo, tx)

    afterEach { clearMocks(repo, creator, tracker, storage, publisher, suspendHistoryRepo, answers = false) }

    fun member(id: Long = 1L, status: Member.Status = Member.Status.ACTIVE) = Member(
        id = id,
        email = "user$id@test.com",
        handle = "user$id",
        status = status,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
    )

    Given("핸들 변경 시") {

        When("핸들을 바꾸면") {
            val target = member()
            every { repo.find("newhandle") } returns null
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updateHandle(1L, "newhandle")

            Then("핸들만 바뀌고 온라인 상태 트래커는 건드리지 않는다 (id로 keying하므로)") {
                updated.handle shouldBe "newhandle"
                verify(exactly = 0) { tracker.toOffline(any()) }
                verify(exactly = 0) { tracker.toOnline(any()) }
            }
        }
    }

    Given("온라인 상태 조회 시") {

        When("레디스에 온라인으로 표시되어 있으면") {
            every { repo.find("user1") } returns member()
            every { tracker.checkOnline(1L) } returns mapOf(1L to true)

            Then("true를 반환한다") {
                service.isOnline("user1") shouldBe true
            }
        }

        When("레디스에 없으면(TTL 만료 포함)") {
            every { repo.find("user1") } returns member()
            every { tracker.checkOnline(1L) } returns mapOf(1L to false)

            Then("false를 반환한다") {
                service.isOnline("user1") shouldBe false
            }
        }
    }

    Given("회원 정지 시") {

        When("존재하는 활성 회원을 정지하면") {
            val target = member(status = Member.Status.ACTIVE)
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }
            every { suspendHistoryRepo.save(any()) } answers { firstArg() }

            service.suspendMember(1L, "policy violation")

            Then("상태가 SUSPENDED로 바뀌어 저장되고 정지 이력이 남는다") {
                verify { repo.save(match { it.status == Member.Status.SUSPENDED }) }
                verify { suspendHistoryRepo.save(match<MemberSuspendHistory> { it.reason == "policy violation" }) }
            }
        }

        When("이미 탈퇴한 회원을 정지하려 하면") {
            every { repo.find(2L) } returns member(id = 2L, status = Member.Status.WITHDRAWN)

            Then("400 LanglezException이 발생한다") {
                val ex = shouldThrow<LanglezException> { service.suspendMember(2L) }
                ex.status.value() shouldBe 400
            }
        }

        When("존재하지 않는 회원을 정지하려 하면") {
            every { repo.find(999L) } returns null

            Then("404 LanglezException이 발생한다") {
                val ex = shouldThrow<LanglezException> { service.suspendMember(999L) }
                ex.status.value() shouldBe 404
            }
        }
    }

    Given("회원 탈퇴 시") {
        When("존재하는 회원을 탈퇴 처리하면") {
            val target = member()
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            service.withdrawMember(1L)

            // 두 Then 사이에 afterEach 의 clearMocks 가 돌아 호출 기록이 지워진다.
            // service.withdrawMember(1L) 는 When 블록에서 한 번만 실행되므로
            // 이 실행에서 나온 상호작용은 반드시 같은 Then 안에서 함께 검증한다.
            Then("상태가 WITHDRAWN으로 바뀌어 저장되고 MemberWithdrawnEvent가 발행된다") {
                verify { repo.save(match { it.status == Member.Status.WITHDRAWN }) }
                verify { publisher.publishEvent(MemberWithdrawnEvent(1L)) }
            }
        }
    }

    Given("프로필 이미지 업로드 시") {

        When("presignProfileUrl을 호출하면") {
            val result = Storage.PresignedResult(key = "member/2026-08-03/uuid_photo.jpg", presigned = "https://presigned.url")
            every { storage.presign(1L, "member", Storage.Type.IMAGE, "photo.jpg") } returns result

            val presigned = service.presignProfileUrl(1L, "photo.jpg")

            Then("Storage가 반환한 결과를 그대로 반환한다") {
                presigned shouldBe result
            }
        }

        When("updateProfileUrl을 호출하면") {
            val target = member()
            every { repo.find(1L) } returns target
            every { storage.attach("key123", 1L) } returns "https://cdn.langlez.com/key123"
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updateProfileUrl(1L, "key123")

            Then("member.imageUrl에 attach 결과 URL이 반영되어 저장된다") {
                updated.imageUrl shouldBe "https://cdn.langlez.com/key123"
                verify { repo.save(match { it.imageUrl == "https://cdn.langlez.com/key123" }) }
            }
        }
    }
})
