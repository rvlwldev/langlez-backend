package com.langlez.chat.application

import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRole
import com.langlez.redis.ratelimit.DailyRateLimiter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*

class ChatRoomCreatorTest : BehaviorSpec({

    val chatRoomRepository = mockk<ChatRoomRepository>()
    val memberRepository = mockk<MemberRepository>()
    val dailyRateLimiter = mockk<DailyRateLimiter>()

    val creator = ChatRoomCreator(
        chatRoomRepository,
        memberRepository,
        dailyRateLimiter
    )

    afterEach {
        clearMocks(
            chatRoomRepository,
            memberRepository,
            dailyRateLimiter,
            answers = false
        )
    }

    fun createMember(id: Long, username: String = "user$id", nickname: String = "User $id") = Member(
        id = id,
        email = "$username@example.com",
        username = username,
        nickname = nickname,
        provider = MemberProvider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = nickname
    )

    Given("getOrCreateRoom 호출 시") {
        val requesterId = 1L
        val lowId = 1L
        val highId = 2L

        When("이미 방이 존재하면 (double-checked lock re-check)") {
            val existingRoom = ChatRoom(id = "room1", participantIds = listOf(lowId, highId))
            every { chatRoomRepository.findByParticipants(lowId, highId) } returns existingRoom

            Then("이미 존재하는 방을 그대로 반환한다") {
                val result = creator.getOrCreateRoom(requesterId, highId, lowId)
                result shouldBe existingRoom
                verify(exactly = 0) { memberRepository.findById(any()) }
                verify(exactly = 0) { dailyRateLimiter.tryConsume(any(), any()) }
                verify(exactly = 0) { chatRoomRepository.save(any()) }
            }
        }

        When("방이 존재하지 않고, MEMBER 등급인 경우") {
            val requester = createMember(requesterId)
            requester.role = MemberRole.MEMBER

            every { chatRoomRepository.findByParticipants(lowId, highId) } returns null
            every { memberRepository.findById(requesterId) } returns requester
            every { dailyRateLimiter.tryConsume("chat:room:$requesterId", 5) } returns true
            every { chatRoomRepository.save(any()) } answers { firstArg() }

            Then("새로운 방을 생성하고 저장한다") {
                val result = creator.getOrCreateRoom(requesterId, highId, lowId)
                result.participantIds shouldBe listOf(lowId, highId)
                result.readStatus[requesterId] shouldNotBe null
                verify(exactly = 1) { chatRoomRepository.save(any()) }
                verify(exactly = 1) { dailyRateLimiter.tryConsume("chat:room:$requesterId", 5) }
            }
        }

        When("방이 존재하지 않고, MEMBER가 하루 5명 초과로 새 방 생성을 요청하면") {
            val requester = createMember(requesterId)
            requester.role = MemberRole.MEMBER

            every { chatRoomRepository.findByParticipants(lowId, highId) } returns null
            every { memberRepository.findById(requesterId) } returns requester
            every { dailyRateLimiter.tryConsume("chat:room:$requesterId", 5) } returns false

            Then("429 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    creator.getOrCreateRoom(requesterId, highId, lowId)
                }
                ex.status shouldBe 429
                ex.message shouldBe "chat.room-daily-limit-exceeded"
                verify(exactly = 0) { chatRoomRepository.save(any()) }
            }
        }

        When("방이 존재하지 않고, PREMIUM 등급인 경우") {
            val requester = createMember(requesterId)
            requester.role = MemberRole.PREMIUM

            every { chatRoomRepository.findByParticipants(lowId, highId) } returns null
            every { memberRepository.findById(requesterId) } returns requester
            every { dailyRateLimiter.tryConsume("chat:room:$requesterId", 30) } returns true
            every { chatRoomRepository.save(any()) } answers { firstArg() }

            Then("limit 30으로 tryConsume을 호출하고 방이 생성된다") {
                val result = creator.getOrCreateRoom(requesterId, highId, lowId)
                result.participantIds shouldBe listOf(lowId, highId)
                verify(exactly = 1) { dailyRateLimiter.tryConsume("chat:room:$requesterId", 30) }
                verify(exactly = 1) { chatRoomRepository.save(any()) }
            }
        }
    }
})
