package com.langlez.matching.application

import com.langlez.chat.application.ChatService
import com.langlez.chat.domain.ChatRoom
import com.langlez.core.LanglezException
import com.langlez.matching.api.MatchingResponse
import com.langlez.matching.domain.MatchingQueueRepository
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileRepository
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.RelationshipRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class MatchingServiceTest : BehaviorSpec({

    val queueRepository = mockk<MatchingQueueRepository>()
    val profileRepository = mockk<ProfileRepository>()
    val memberRepository = mockk<MemberRepository>()
    val relationshipRepository = mockk<RelationshipRepository>()
    val chatService = mockk<ChatService>()
    val matchBroadcaster = mockk<MatchBroadcaster>(relaxed = true)

    val service = MatchingService(
        queueRepository,
        profileRepository,
        memberRepository,
        relationshipRepository,
        chatService,
        matchBroadcaster,
    )

    afterEach {
        clearMocks(
            queueRepository, profileRepository, memberRepository,
            relationshipRepository, chatService, matchBroadcaster,
            answers = false
        )
    }

    fun member(id: Long, username: String = "user$id") = Member(
        id = id,
        email = "$username@test.com",
        username = username,
        nickname = "User$id",
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = "User$id",
    )

    fun profile(
        id: Long,
        languageLevel: Profile.LanguageLevel? = Profile.LanguageLevel.INTERMEDIATE,
        interests: Set<String> = emptySet(),
    ) = Profile(id = id, member = member(id), languageLevel = languageLevel, interests = interests.toMutableSet())

    Given("joinQueue 호출 시") {
        val memberId = 1L

        When("이미 큐에 있으면") {
            every { queueRepository.isQueued(memberId) } returns true

            Then("409 예외가 발생한다") {
                shouldThrow<LanglezException> { service.joinQueue(memberId) }.status shouldBe 409
                verify(exactly = 0) { profileRepository.findProfile(any()) }
            }
        }

        When("프로필이 없으면") {
            every { queueRepository.isQueued(memberId) } returns false
            every { profileRepository.findProfile(memberId) } returns null

            Then("404 예외가 발생한다") {
                shouldThrow<LanglezException> { service.joinQueue(memberId) }.status shouldBe 404
            }
        }

        When("languageLevel이 설정되지 않았으면") {
            every { queueRepository.isQueued(memberId) } returns false
            every { profileRepository.findProfile(memberId) } returns profile(memberId, languageLevel = null)

            Then("400 예외가 발생한다") {
                shouldThrow<LanglezException> { service.joinQueue(memberId) }.status shouldBe 400
            }
        }

        When("정상 참가했지만 매칭 후보가 없으면") {
            every { queueRepository.isQueued(memberId) } returns false
            every { profileRepository.findProfile(memberId) } returns profile(memberId, Profile.LanguageLevel.INTERMEDIATE)
            every { queueRepository.add(memberId, 1000.0) } just runs
            every { queueRepository.saveJoinedAt(memberId, any()) } just runs
            every { queueRepository.score(memberId) } returns 1000.0
            every { queueRepository.findJoinedAt(memberId) } returns Instant.now()
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns emptyList()

            Then("WAITING 상태를 반환하고 채팅방을 만들지 않는다") {
                val result = service.joinQueue(memberId)
                result.status shouldBe MatchingResponse.QueueStatus.Status.WAITING
                result.roomId shouldBe null
                verify { queueRepository.add(memberId, 1000.0) }
                verify(exactly = 0) { chatService.getOrCreateRoom(any(), any()) }
            }
        }
    }

    Given("attemptMatch 호출 시 (매칭 시도 로직)") {
        val memberId = 1L
        // 실제 wall-clock에 가까운 시각을 사용해 tolerance가 기본값(200)으로 계산되게 한다.
        val now = Instant.now()

        beforeEach {
            every { queueRepository.score(memberId) } returns 1000.0
            every { queueRepository.findJoinedAt(memberId) } returns now
            every { profileRepository.findProfile(memberId) } returns
                profile(memberId, Profile.LanguageLevel.INTERMEDIATE, interests = setOf("movie", "music"))
        }

        When("점수 범위 내 후보가 아무도 없으면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns emptyList()

            Then("null(대기 유지)을 반환한다") {
                service.attemptMatch(memberId) shouldBe null
            }
        }

        When("유일한 후보가 차단 관계이면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L)
            every { relationshipRepository.findBlock(memberId, 2L) } returns Block(memberId, 2L)
            every { relationshipRepository.findBlock(2L, memberId) } returns null

            Then("매칭시키지 않고 null을 반환한다") {
                service.attemptMatch(memberId) shouldBe null
                verify(exactly = 0) { chatService.getOrCreateRoom(any(), any()) }
            }
        }

        When("차단되지 않은 후보가 있으면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L)
            every { relationshipRepository.findBlock(memberId, 2L) } returns null
            every { relationshipRepository.findBlock(2L, memberId) } returns null
            every { profileRepository.findProfile(2L) } returns
                profile(2L, Profile.LanguageLevel.INTERMEDIATE, interests = setOf("movie"))
            every { queueRepository.findJoinedAt(2L) } returns now
            every { queueRepository.score(2L) } returns 1000.0
            every { queueRepository.remove(2L) } returns true
            every { queueRepository.removeJoinedAt(2L) } just runs
            every { queueRepository.remove(memberId) } returns true
            every { queueRepository.removeJoinedAt(memberId) } just runs
            every { memberRepository.findById(memberId) } returns member(memberId)
            every { memberRepository.findById(2L) } returns member(2L)
            every { chatService.getOrCreateRoom(memberId, "user2") } returns ChatRoom(id = "room1", participantIds = listOf(1L, 2L))

            Then("매칭에 성공하고 채팅방을 만들어 양쪽에 브로드캐스트한다") {
                val result = service.attemptMatch(memberId)
                result?.status shouldBe MatchingResponse.QueueStatus.Status.MATCHED
                result?.roomId shouldBe "room1"

                verify { chatService.getOrCreateRoom(memberId, "user2") }
                verify { matchBroadcaster.broadcastMatched(memberId, "room1", "user2") }
                verify { matchBroadcaster.broadcastMatched(2L, "room1", "user1") }
                verify { queueRepository.remove(memberId) }
                verify { queueRepository.remove(2L) }
            }
        }

        When("공통 관심사가 더 많은 후보가 여럿 중에 있으면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L, 3L)
            every { relationshipRepository.findBlock(any(), any()) } returns null
            // candidate 2: 공통 관심사 1개("movie")
            every { profileRepository.findProfile(2L) } returns
                profile(2L, Profile.LanguageLevel.INTERMEDIATE, interests = setOf("movie"))
            // candidate 3: 공통 관심사 2개("movie", "music") - 더 많음
            every { profileRepository.findProfile(3L) } returns
                profile(3L, Profile.LanguageLevel.INTERMEDIATE, interests = setOf("movie", "music"))
            every { queueRepository.findJoinedAt(2L) } returns now
            every { queueRepository.findJoinedAt(3L) } returns now
            every { queueRepository.score(3L) } returns 1000.0
            every { queueRepository.remove(3L) } returns true
            every { queueRepository.removeJoinedAt(3L) } just runs
            every { queueRepository.remove(memberId) } returns true
            every { queueRepository.removeJoinedAt(memberId) } just runs
            every { memberRepository.findById(memberId) } returns member(memberId)
            every { memberRepository.findById(3L) } returns member(3L)
            every { chatService.getOrCreateRoom(memberId, "user3") } returns ChatRoom(id = "room2", participantIds = listOf(1L, 3L))

            Then("공통 관심사가 더 많은 후보(3번)와 매칭된다") {
                val result = service.attemptMatch(memberId)
                result?.roomId shouldBe "room2"
                verify { chatService.getOrCreateRoom(memberId, "user3") }
                verify(exactly = 0) { chatService.getOrCreateRoom(memberId, "user2") }
                verify(exactly = 0) { queueRepository.remove(2L) }
            }
        }
    }
})
