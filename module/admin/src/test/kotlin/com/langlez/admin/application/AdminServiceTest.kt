package com.langlez.admin.application

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.core.MemberPresenceTracker
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

class AdminServiceTest : BehaviorSpec({

    val memberRepository = mockk<MemberRepository>()
    val memberPresenceTracker = mockk<MemberPresenceTracker>()
    val chatRoomRepository = mockk<ChatRoomRepository>()
    val chatMessageRepository = mockk<ChatMessageRepository>()

    val adminService = AdminService(
        memberRepository,
        memberPresenceTracker,
        chatRoomRepository,
        chatMessageRepository
    )

    afterEach {
        clearMocks(memberRepository, memberPresenceTracker, chatRoomRepository, chatMessageRepository)
    }

    fun createMember(id: Long, username: String, nickname: String) = Member(
        id = id,
        email = "$username@test.com",
        username = username,
        nickname = nickname,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = nickname
    )

    Given("대시보드 조회 시") {
        every { memberRepository.countAll() } returns 10L
        every { memberPresenceTracker.countOnline() } returns 3L

        When("대시보드 데이터를 가져오면") {
            val result = adminService.getDashboard()

            Then("총 회원 수와 접속 회원 수가 정확히 반환되어야 한다") {
                result.totalMembers shouldBe 10L
                result.onlineMembers shouldBe 3L
            }
        }
    }

    Given("가입자 목록 조회 시") {
        val m1 = createMember(1L, "user1", "nick1")
        val m2 = createMember(2L, "user2", "nick2")

        every { memberRepository.findAll(null, 2) } returns listOf(m2, m1)
        every { memberPresenceTracker.isOnline(1L) } returns false
        every { memberPresenceTracker.isOnline(2L) } returns true

        When("목록을 조회하면") {
            val result = adminService.getUsers(null, 2)

            Then("회원 정보와 온라인 여부가 정상 매핑되어야 한다") {
                result shouldHaveSize 2
                result[0].username shouldBe "user2"
                result[0].online shouldBe true
                result[1].username shouldBe "user1"
                result[1].online shouldBe false
            }
        }
    }

    Given("특정 유저의 채팅방 목록 조회 시") {
        val user = createMember(1L, "user1", "nick1")
        val recipient = createMember(2L, "user2", "nick2")
        val room = ChatRoom(
            id = "room123",
            participantIds = listOf(1L, 2L),
            lastMessagePreview = "Hello",
            lastMessageAt = Instant.now()
        )

        When("존재하지 않는 회원명을 입력하면") {
            every { memberRepository.findByUsername("nonexistent") } returns null

            Then("404 LanglezException이 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    adminService.getUserChats("nonexistent")
                }
                ex.status shouldBe 404
            }
        }

        When("정상적인 회원명을 입력하면") {
            every { memberRepository.findByUsername("user1") } returns user
            every { chatRoomRepository.findByParticipant(1L, null, 100) } returns listOf(room)
            every { memberRepository.findByIds(listOf(2L)) } returns listOf(recipient)

            val result = adminService.getUserChats("user1")

            Then("상대방 닉네임과 함께 채팅방 정보가 반환된다") {
                result shouldHaveSize 1
                result[0].roomId shouldBe "room123"
                result[0].participantUsernames shouldBe listOf("user1", "user2")
                result[0].lastMessagePreview shouldBe "Hello"
            }
        }
    }

    Given("전체 채팅방 목록 조회 시") {
        val room = ChatRoom(id = "room1", participantIds = listOf(1L, 2L))
        val m1 = createMember(1L, "user1", "nick1")
        val m2 = createMember(2L, "user2", "nick2")

        every { chatRoomRepository.findAllRooms(null, 2) } returns listOf(room)
        every { memberRepository.findByIds(listOf(1L, 2L)) } returns listOf(m1, m2)

        When("조회하면") {
            val result = adminService.getAllChats(null, 2)

            Then("방 정보와 모든 참여자명 목록이 매핑되어야 한다") {
                result shouldHaveSize 1
                result[0].roomId shouldBe "room1"
                result[0].participantUsernames shouldBe listOf("user1", "user2")
            }
        }
    }

    Given("채팅방 메시지 히스토리 조회 시") {
        val m1 = createMember(1L, "user1", "nick1")
        val msg1 = ChatMessage(id = "m1", roomId = "room1", senderId = 1L, type = ChatMessage.Type.TEXT, content = "Hello")
        val msg2 = ChatMessage(id = "m2", roomId = "room1", senderId = 1L, type = ChatMessage.Type.TEXT, content = "World")

        every { chatMessageRepository.findByRoom("room1", null, 2) } returns listOf(msg2, msg1)
        every { memberRepository.findByIds(listOf(1L)) } returns listOf(m1)

        When("조회하면") {
            val result = adminService.getChatRoomMessages("room1", null, 2)

            Then("메시지가 시간 순서(오래된 순)로 뒤집혀 렌더링을 위해 정렬되어야 한다") {
                result shouldHaveSize 2
                result[0].id shouldBe "m1"
                result[0].content shouldBe "Hello"
                result[1].id shouldBe "m2"
                result[1].content shouldBe "World"
            }
        }
    }

    Given("특정 시각 이후의 메시지 폴링 시") {
        val m1 = createMember(1L, "user1", "nick1")
        val since = Instant.now()
        val msg = ChatMessage(id = "m3", roomId = "room1", senderId = 1L, type = ChatMessage.Type.TEXT, content = "New Message")

        every { chatMessageRepository.findByRoomSince("room1", since) } returns listOf(msg)
        every { memberRepository.findByIds(listOf(1L)) } returns listOf(m1)

        When("조회하면") {
            val result = adminService.getChatRoomMessagesSince("room1", since)

            Then("이후 생성된 메시지 리스트가 정상 변환되어야 한다") {
                result shouldHaveSize 1
                result[0].id shouldBe "m3"
                result[0].content shouldBe "New Message"
            }
        }
    }

    Given("첨부파일 모아보기 시") {
        val m1 = createMember(1L, "user1", "nick1")
        val imgMsg = ChatMessage(
            id = "m4",
            roomId = "room1",
            senderId = 1L,
            type = ChatMessage.Type.IMAGE,
            fileUrl = "http://test.com/image.png"
        )

        every { chatMessageRepository.findAttachments(null, 1) } returns listOf(imgMsg)
        every { memberRepository.findByIds(listOf(1L)) } returns listOf(m1)

        When("조회하면") {
            val result = adminService.getAttachments(null, 1)

            Then("이미지/비디오/오디오 형태의 첨부파일 메시지가 정상 반환되어야 한다") {
                result shouldHaveSize 1
                result[0].id shouldBe "m4"
                result[0].fileUrl shouldBe "http://test.com/image.png"
                result[0].type shouldBe "IMAGE"
            }
        }
    }
})
