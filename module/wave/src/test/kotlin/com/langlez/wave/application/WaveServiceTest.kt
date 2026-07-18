package com.langlez.wave.application

import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveRoomRepository
import com.langlez.wave.infrastructure.WaveViewerTracker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*

class WaveServiceTest : BehaviorSpec({

    val waveRoomRepository = mockk<WaveRoomRepository>()
    val memberRepository = mockk<MemberRepository>()
    val relationshipRepository = mockk<RelationshipRepository>()
    val viewerTracker = mockk<WaveViewerTracker>(relaxed = true)
    val notificator = mockk<com.langlez.core.Notificator>(relaxed = true)

    val service = WaveService(
        waveRoomRepository,
        memberRepository,
        relationshipRepository,
        viewerTracker,
        notificator
    )

    afterEach {
        clearMocks(waveRoomRepository, memberRepository, relationshipRepository, viewerTracker, notificator, answers = false)
    }

    fun createMember(id: Long, role: Member.Role = Member.Role.MEMBER, username: String = "user$id") = Member(
        id = id,
        email = "$username@example.com",
        username = username,
        nickname = "Nick $id",
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = "Nick $id",
        role = role
    )

    Given("startLive 호출 시") {
        val broadcasterId = 1L

        When("요청자가 MEMBER(무료 회원)이면") {
            every { memberRepository.findById(broadcasterId) } returns createMember(broadcasterId, Member.Role.MEMBER)

            Then("403 예외가 발생하고 방이 생성되지 않는다") {
                shouldThrow<LanglezException> {
                    service.startLive(broadcasterId)
                }.status shouldBe 403
                verify(exactly = 0) { waveRoomRepository.save(any()) }
            }
        }

        When("요청자가 PREMIUM이면") {
            val savedRoom = WaveRoom(id = 10L, broadcasterId = broadcasterId)
            val broadcaster = createMember(broadcasterId, Member.Role.PREMIUM)
            every { memberRepository.findById(broadcasterId) } returns broadcaster
            every { waveRoomRepository.save(any()) } returns savedRoom
            every { relationshipRepository.findFollowers(broadcasterId, null, any()) } returns listOf(
                Follow(followerId = 2L, followedId = broadcasterId),
                Follow(followerId = 3L, followedId = broadcasterId)
            )

            Then("방을 생성하고 팔로워 목록을 조회한다") {
                val result = service.startLive(broadcasterId)
                result shouldBe savedRoom
                verify(exactly = 1) { waveRoomRepository.save(any()) }
                verify(exactly = 1) { relationshipRepository.findFollowers(broadcasterId, null, any()) }
                verify(exactly = 1) {
                    notificator.notify(2L, "wave.live-started", "${broadcaster.nickname}님이 라이브를 시작했어요", "지금 바로 들어와보세요!")
                    notificator.notify(3L, "wave.live-started", "${broadcaster.nickname}님이 라이브를 시작했어요", "지금 바로 들어와보세요!")
                }
            }
        }

        When("요청자가 ADMIN이면") {
            val savedRoom = WaveRoom(id = 11L, broadcasterId = broadcasterId)
            every { memberRepository.findById(broadcasterId) } returns createMember(broadcasterId, Member.Role.ADMIN)
            every { waveRoomRepository.save(any()) } returns savedRoom
            every { relationshipRepository.findFollowers(broadcasterId, null, any()) } returns emptyList()

            Then("방을 생성한다") {
                val result = service.startLive(broadcasterId)
                result shouldBe savedRoom
            }
        }
    }

    Given("endLive 호출 시") {
        val broadcasterId = 1L
        val roomId = 100L

        When("방이 존재하지 않으면") {
            every { waveRoomRepository.findById(roomId) } returns null

            Then("404 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.endLive(broadcasterId, roomId)
                }.status shouldBe 404
            }
        }

        When("요청자가 방송자가 아니면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId)
            every { waveRoomRepository.findById(roomId) } returns room

            Then("403 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.endLive(999L, roomId)
                }.status shouldBe 403
            }
        }

        When("이미 종료된 방이면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, endedAt = java.time.Instant.now())
            every { waveRoomRepository.findById(roomId) } returns room

            Then("409 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.endLive(broadcasterId, roomId)
                }.status shouldBe 409
            }
        }

        When("방송자 본인이 정상적으로 종료하면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId)
            every { waveRoomRepository.findById(roomId) } returns room
            every { waveRoomRepository.save(any()) } answers { firstArg() }

            Then("방이 종료 처리된다") {
                val result = service.endLive(broadcasterId, roomId)
                result.isEnded() shouldBe true
                verify { waveRoomRepository.save(room) }
            }
        }
    }

    Given("getActiveRooms 호출 시") {
        Then("진행 중인 방 목록을 반환한다") {
            val rooms = listOf(WaveRoom(id = 1L, broadcasterId = 1L), WaveRoom(id = 2L, broadcasterId = 2L))
            every { waveRoomRepository.findActive(null, 20) } returns rooms

            val result = service.getActiveRooms(null, 20)
            result shouldHaveSize 2
        }
    }

    Given("getRoom 호출 시") {
        When("존재하지 않는 방이면") {
            every { waveRoomRepository.findById(500L) } returns null

            Then("404 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.getRoom(500L)
                }.status shouldBe 404
            }
        }
    }
})
