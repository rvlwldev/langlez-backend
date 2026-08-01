package com.langlez.wave.application

import com.langlez.core.LanglezException
import com.langlez.core.Notificator
import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRole
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
    val notificator = mockk<Notificator>(relaxed = true)
    val broadcaster = mockk<WaveBroadcaster>(relaxed = true)

    val service = WaveService(
        waveRoomRepository,
        memberRepository,
        relationshipRepository,
        viewerTracker,
        notificator,
        broadcaster
    )

    afterEach {
        clearMocks(waveRoomRepository, memberRepository, relationshipRepository, viewerTracker, notificator, broadcaster, answers = false)
    }

    fun createMember(id: Long, role: MemberRole = MemberRole.MEMBER, username: String = "user$id") = Member(
        id = id,
        email = "$username@example.com",
        username = username,
        nickname = "Nick $id",
        provider = MemberProvider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = "Nick $id",
        role = role
    )

    Given("startLive 호출 시") {
        val broadcasterId = 1L
        val title = "Enjoy Live Audio"
        val maxParticipants = 6

        When("요청자가 MEMBER(무료 회원)이면") {
            every { memberRepository.findById(broadcasterId) } returns createMember(broadcasterId, MemberRole.MEMBER)

            Then("403 예외가 발생하고 방이 생성되지 않는다") {
                val ex = shouldThrow<LanglezException> {
                    service.startLive(broadcasterId, title, maxParticipants)
                }
                ex.status shouldBe 403
                verify(exactly = 0) { waveRoomRepository.save(any()) }
            }
        }

        When("인원수가 4~8 범위를 벗어나면(e.g., 2 또는 10)") {
            every { memberRepository.findById(broadcasterId) } returns createMember(broadcasterId, MemberRole.PREMIUM)

            Then("400 예외가 발생하고 방이 생성되지 않는다") {
                val ex2 = shouldThrow<LanglezException> {
                    service.startLive(broadcasterId, title, 2)
                }
                ex2.status shouldBe 400

                val ex10 = shouldThrow<LanglezException> {
                    service.startLive(broadcasterId, title, 10)
                }
                ex10.status shouldBe 400

                verify(exactly = 0) { waveRoomRepository.save(any()) }
            }
        }

        When("요청자가 PREMIUM이면") {
            val savedRoom = WaveRoom(id = 10L, broadcasterId = broadcasterId, title = title, maxParticipants = maxParticipants)
            val broadcasterMember = createMember(broadcasterId, MemberRole.PREMIUM)
            every { memberRepository.findById(broadcasterId) } returns broadcasterMember
            every { waveRoomRepository.save(any()) } returns savedRoom
            every { relationshipRepository.findFollowers(broadcasterId, null, any()) } returns listOf(
                Follow(followerId = 2L, followedId = broadcasterId),
                Follow(followerId = 3L, followedId = broadcasterId)
            )

            Then("방을 생성하고 팔로워 목록을 조회한다") {
                val result = service.startLive(broadcasterId, title, maxParticipants)
                result shouldBe savedRoom
                result.title shouldBe title
                result.maxParticipants shouldBe maxParticipants
                verify(exactly = 1) { waveRoomRepository.save(any()) }
                verify(exactly = 1) { relationshipRepository.findFollowers(broadcasterId, null, any()) }
                verify(exactly = 1) {
                    notificator.notify(2L, "wave.live-started", "${broadcasterMember.nickname}님이 라이브를 시작했어요", "지금 바로 들어와보세요!")
                    notificator.notify(3L, "wave.live-started", "${broadcasterMember.nickname}님이 라이브를 시작했어요", "지금 바로 들어와보세요!")
                }
            }
        }
    }

    Given("updateTitle 호출 시") {
        val broadcasterId = 1L
        val roomId = 100L

        When("호스트가 제목을 변경하면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, title = "Old Title", maxParticipants = 6)
            every { waveRoomRepository.findById(roomId) } returns room
            every { waveRoomRepository.save(any()) } answers { firstArg() }

            Then("제목이 성공적으로 변경된다") {
                val updated = service.updateTitle(broadcasterId, roomId, "New Title")
                updated.title shouldBe "New Title"
                verify(exactly = 1) { waveRoomRepository.save(room) }
            }
        }

        When("호스트가 아닌 유저가 제목 변경을 시도하면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, title = "Old Title", maxParticipants = 6)
            every { waveRoomRepository.findById(roomId) } returns room

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updateTitle(999L, roomId, "New Title")
                }
                ex.status shouldBe 403
                ex.message shouldBe "wave.not-broadcaster"
            }
        }

        When("종료된 방의 제목 변경을 시도하면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, title = "Old Title", maxParticipants = 6, endedAt = java.time.Instant.now())
            every { waveRoomRepository.findById(roomId) } returns room

            Then("409 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updateTitle(broadcasterId, roomId, "New Title")
                }
                ex.status shouldBe 409
                ex.message shouldBe "wave.already-ended"
            }
        }
    }

    Given("muteMember 호출 시") {
        val broadcasterId = 1L
        val roomId = 100L
        val targetMemberId = 5L

        When("호스트가 참여자 음소거 명령을 보내면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, title = "Title", maxParticipants = 6)
            every { waveRoomRepository.findById(roomId) } returns room

            Then("대상 유저에게 음소거 명령이 전송된다") {
                service.muteMember(broadcasterId, roomId, targetMemberId)
                verify(exactly = 1) {
                    broadcaster.sendMuteToUser(targetMemberId, WaveMutePayload(roomId))
                }
            }
        }

        When("호스트가 아닌 유저가 음소거를 시도하면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, title = "Title", maxParticipants = 6)
            every { waveRoomRepository.findById(roomId) } returns room

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.muteMember(999L, roomId, targetMemberId)
                }
                ex.status shouldBe 403
                ex.message shouldBe "wave.not-broadcaster"
            }
        }
    }

    Given("kickMember 호출 시") {
        val broadcasterId = 1L
        val roomId = 100L
        val targetMemberId = 5L

        When("호스트가 참여자 강퇴를 요청하면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, title = "Title", maxParticipants = 6)
            every { waveRoomRepository.findById(roomId) } returns room

            Then("viewerTracker에서 kick 처리되고 WebSocket으로 강퇴 명령이 전달된다") {
                service.kickMember(broadcasterId, roomId, targetMemberId)
                verify(exactly = 1) { viewerTracker.kickUser(roomId, targetMemberId) }
                verify(exactly = 1) { broadcaster.sendKickToUser(targetMemberId, WaveKickPayload(roomId)) }
            }
        }

        When("호스트가 아닌 유저가 강퇴를 시도하면") {
            val room = WaveRoom(id = roomId, broadcasterId = broadcasterId, title = "Title", maxParticipants = 6)
            every { waveRoomRepository.findById(roomId) } returns room

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.kickMember(999L, roomId, targetMemberId)
                }
                ex.status shouldBe 403
                ex.message shouldBe "wave.not-broadcaster"
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
