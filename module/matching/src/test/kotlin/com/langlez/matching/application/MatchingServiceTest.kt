package com.langlez.matching.application

import com.langlez.chat.application.ChatService
import com.langlez.chat.domain.ChatRoom
import com.langlez.core.LanglezException
import com.langlez.interest.application.InterestService
import com.langlez.matching.api.MatchingResponse
import com.langlez.matching.domain.MatchingQueueFilter
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
import java.time.LocalDate

class MatchingServiceTest : BehaviorSpec({

    val queueRepository = mockk<MatchingQueueRepository>()
    val profileRepository = mockk<ProfileRepository>()
    val memberRepository = mockk<MemberRepository>()
    val relationshipRepository = mockk<RelationshipRepository>()
    val chatService = mockk<ChatService>()
    val interestService = mockk<InterestService>(relaxed = true)
    val matchBroadcaster = mockk<MatchBroadcaster>(relaxed = true)

    val service = MatchingService(
        queueRepository,
        profileRepository,
        memberRepository,
        relationshipRepository,
        chatService,
        interestService,
        matchBroadcaster,
    )

    beforeEach {
        every { queueRepository.findMetaInBatch(any()) } returns emptyMap()
    }

    afterEach {
        clearMocks(
            queueRepository, profileRepository, memberRepository,
            relationshipRepository, chatService, interestService, matchBroadcaster,
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
    ) = Profile(id = id, member = member(id), languageLevel = languageLevel)

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
            every { queueRepository.findFilter(memberId) } returns null
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns emptyList()

            Then("WAITING 상태를 반환하고 채팅방을 만들지 않는다") {
                val result = service.joinQueue(memberId)
                result.status shouldBe MatchingResponse.QueueStatus.Status.WAITING
                result.roomId shouldBe null
                verify { queueRepository.add(memberId, 1000.0) }
                verify(exactly = 0) { chatService.getOrCreateRoom(any(), any()) }
            }
        }

        When("조건 필터와 함께 정상 참가하면") {
            val filter = MatchingQueueFilter(minAge = 20, maxAge = 30, languageLevel = Profile.LanguageLevel.ADVANCED)
            every { queueRepository.isQueued(memberId) } returns false
            every { profileRepository.findProfile(memberId) } returns profile(memberId, Profile.LanguageLevel.ADVANCED)
            every { queueRepository.add(memberId, 2000.0) } just runs
            every { queueRepository.saveJoinedAt(memberId, any()) } just runs
            every { queueRepository.saveFilter(memberId, filter) } just runs
            every { queueRepository.score(memberId) } returns 2000.0
            every { queueRepository.findJoinedAt(memberId) } returns Instant.now()
            every { queueRepository.findFilter(memberId) } returns filter
            every { queueRepository.candidatesInRange(1800.0, 2200.0) } returns emptyList()

            Then("필터를 레포지토리에 저장하고 대기 상태를 반환한다") {
                val result = service.joinQueue(memberId, filter)
                result.status shouldBe MatchingResponse.QueueStatus.Status.WAITING
                verify { queueRepository.saveFilter(memberId, filter) }
            }
        }
    }

    Given("leaveQueue 호출 시") {
        val memberId = 1L
        every { queueRepository.remove(memberId) } returns true
        every { queueRepository.removeJoinedAt(memberId) } just runs
        every { queueRepository.removeFilter(memberId) } just runs

        When("퇴장하면") {
            service.leaveQueue(memberId)

            Then("큐, joinedAt, filter 모두 제거된다") {
                verify { queueRepository.remove(memberId) }
                verify { queueRepository.removeJoinedAt(memberId) }
                verify { queueRepository.removeFilter(memberId) }
            }
        }
    }

    Given("attemptMatch 호출 시 (매칭 시도 로직)") {
        val memberId = 1L
        val now = Instant.now()

        beforeEach {
            every { queueRepository.score(memberId) } returns 1000.0
            every { queueRepository.findJoinedAt(memberId) } returns now
            every { profileRepository.findProfile(memberId) } returns
                profile(memberId, Profile.LanguageLevel.INTERMEDIATE)
            every { interestService.getMemberInterestIds(memberId) } returns setOf(100L, 200L)
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
            every { profileRepository.findProfiles(listOf(2L)) } returns listOf(profile(2L, Profile.LanguageLevel.INTERMEDIATE))
            every { queueRepository.findFilter(memberId) } returns null
            every { queueRepository.findFilter(2L) } returns null

            Then("매칭시키지 않고 null을 반환한다") {
                service.attemptMatch(memberId) shouldBe null
                verify(exactly = 0) { chatService.getOrCreateRoom(any(), any()) }
            }
        }

        When("차단되지 않은 후보가 있으면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L)
            every { relationshipRepository.findBlock(memberId, 2L) } returns null
            every { relationshipRepository.findBlock(2L, memberId) } returns null
            every { profileRepository.findProfiles(listOf(2L)) } returns
                listOf(profile(2L, Profile.LanguageLevel.INTERMEDIATE))
            every { interestService.getMemberInterestIds(2L) } returns setOf(100L)
            every { queueRepository.findJoinedAt(2L) } returns now
            every { queueRepository.score(2L) } returns 1000.0
            every { queueRepository.findFilter(memberId) } returns null
            every { queueRepository.findFilter(2L) } returns null
            every { queueRepository.remove(2L) } returns true
            every { queueRepository.removeJoinedAt(2L) } just runs
            every { queueRepository.removeFilter(2L) } just runs
            every { queueRepository.remove(memberId) } returns true
            every { queueRepository.removeJoinedAt(memberId) } just runs
            every { queueRepository.removeFilter(memberId) } just runs
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
                verify { queueRepository.removeFilter(memberId) }
                verify { queueRepository.removeFilter(2L) }
            }
        }

        When("내 필터 조건에 상대방 프로필이 부합하지 않으면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L)
            every { relationshipRepository.findBlock(memberId, 2L) } returns null
            every { relationshipRepository.findBlock(2L, memberId) } returns null
            every { queueRepository.findFilter(memberId) } returns MatchingQueueFilter(minAge = 25)
            every { queueRepository.findFilter(2L) } returns null

            val candidateProfile = profile(2L, Profile.LanguageLevel.INTERMEDIATE)
            candidateProfile.birthDay = LocalDate.now().minusYears(20)
            every { profileRepository.findProfiles(listOf(2L)) } returns listOf(candidateProfile)

            Then("매칭되지 않고 null을 반환한다") {
                service.attemptMatch(memberId) shouldBe null
                verify(exactly = 0) { chatService.getOrCreateRoom(any(), any()) }
            }
        }

        When("상대방 필터 조건에 내 프로필이 부합하지 않으면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L)
            every { relationshipRepository.findBlock(memberId, 2L) } returns null
            every { relationshipRepository.findBlock(2L, memberId) } returns null

            val myProfile = profile(memberId, Profile.LanguageLevel.INTERMEDIATE)
            myProfile.birthDay = LocalDate.now().minusYears(20)
            every { profileRepository.findProfile(memberId) } returns myProfile
            every { queueRepository.findFilter(memberId) } returns null

            val candidateProfile = profile(2L, Profile.LanguageLevel.INTERMEDIATE)
            candidateProfile.birthDay = LocalDate.now().minusYears(30)
            every { profileRepository.findProfiles(listOf(2L)) } returns listOf(candidateProfile)
            every { queueRepository.findFilter(2L) } returns MatchingQueueFilter(minAge = 25)

            Then("상대방 조건 불만족으로 매칭되지 않고 null을 반환한다") {
                service.attemptMatch(memberId) shouldBe null
                verify(exactly = 0) { chatService.getOrCreateRoom(any(), any()) }
            }
        }

        When("공통 관심사가 더 많은 후보가 여럿 중에 있으면") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L, 3L)
            every { relationshipRepository.findBlock(any(), any()) } returns null
            every { queueRepository.findFilter(any()) } returns null
            // candidate 2: 공통 관심사 1개("movie"), candidate 3: 공통 관심사 2개("movie", "music") - 더 많음
            every { profileRepository.findProfiles(listOf(2L, 3L)) } returns listOf(
                profile(2L, Profile.LanguageLevel.INTERMEDIATE),
                profile(3L, Profile.LanguageLevel.INTERMEDIATE),
            )
            every { interestService.getMemberInterestIds(2L) } returns setOf(100L)
            every { interestService.getMemberInterestIds(3L) } returns setOf(100L, 200L)
            every { queueRepository.findJoinedAt(2L) } returns now
            every { queueRepository.findJoinedAt(3L) } returns now
            every { queueRepository.score(3L) } returns 1000.0
            every { queueRepository.remove(3L) } returns true
            every { queueRepository.removeJoinedAt(3L) } just runs
            every { queueRepository.removeFilter(3L) } just runs
            every { queueRepository.remove(memberId) } returns true
            every { queueRepository.removeJoinedAt(memberId) } just runs
            every { queueRepository.removeFilter(memberId) } just runs
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

        When("connect 실패 시 (DB/네트워크 예외 발생)") {
            every { queueRepository.candidatesInRange(800.0, 1200.0) } returns listOf(2L)
            every { relationshipRepository.findBlock(memberId, 2L) } returns null
            every { relationshipRepository.findBlock(2L, memberId) } returns null
            every { profileRepository.findProfiles(listOf(2L)) } returns listOf(profile(2L))
            every { queueRepository.findJoinedAt(2L) } returns now
            every { queueRepository.score(2L) } returns 1000.0
            every { queueRepository.findFilter(memberId) } returns null
            every { queueRepository.findFilter(2L) } returns null
            every { queueRepository.remove(2L) } returns true
            every { queueRepository.remove(memberId) } returns true
            every { queueRepository.add(2L, 1000.0) } just runs
            every { queueRepository.add(memberId, 1000.0) } just runs
            every { queueRepository.saveJoinedAt(2L, any()) } just runs
            every { queueRepository.saveJoinedAt(memberId, any()) } just runs
            every { memberRepository.findById(memberId) } returns member(memberId)
            every { memberRepository.findById(2L) } returns member(2L)
            every { chatService.getOrCreateRoom(memberId, "user2") } throws RuntimeException("DB Connection Failed")

            Then("예외가 던져지고 두 사용자 모두 큐로 복구된다") {
                shouldThrow<RuntimeException> { service.attemptMatch(memberId) }
                verify { queueRepository.add(2L, 1000.0) }
                verify { queueRepository.add(memberId, 1000.0) }
            }
        }
    }
})



