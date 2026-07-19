package com.langlez.admin.application

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.core.MemberPresenceTracker
import com.langlez.echo.domain.Comment
import com.langlez.echo.domain.CommentRepository
import com.langlez.echo.domain.Post
import com.langlez.echo.domain.PostRepository
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.report.domain.Report
import com.langlez.report.domain.ReportRepository
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
    val attachmentRepository = mockk<AttachmentRepository>()
    val postRepository = mockk<PostRepository>()
    val commentRepository = mockk<CommentRepository>()
    val reportRepository = mockk<ReportRepository>()

    val adminService = AdminService(
        memberRepository,
        memberPresenceTracker,
        chatRoomRepository,
        chatMessageRepository,
        attachmentRepository,
        postRepository,
        commentRepository,
        reportRepository
    )

    afterEach {
        clearMocks(
            memberRepository,
            memberPresenceTracker,
            chatRoomRepository,
            chatMessageRepository,
            attachmentRepository,
            postRepository,
            commentRepository,
            reportRepository
        )
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
        val msg2 = ChatMessage(id = "m2", roomId = "room1", senderId = 1L, type = ChatMessage.Type.TEXT, content = "World", deletedAt = Instant.now())

        every { chatMessageRepository.findByRoom("room1", null, 2) } returns listOf(msg2, msg1)
        every { memberRepository.findByIds(listOf(1L)) } returns listOf(m1)

        When("조회하면") {
            val result = adminService.getChatRoomMessages("room1", null, 2)

            Then("메시지가 시간 순서(오래된 순)로 정렬되고 삭제 여부가 매핑되어야 한다") {
                result shouldHaveSize 2
                result[0].id shouldBe "m1"
                result[0].content shouldBe "Hello"
                result[0].deleted shouldBe false
                result[1].id shouldBe "m2"
                result[1].content shouldBe "World"
                result[1].deleted shouldBe true
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
                result[0].deleted shouldBe false
            }
        }
    }

    Given("첨부파일 모아보기 시") {
        val m1 = createMember(1L, "user1", "nick1")
        val attachment = Attachment(
            uploaderId = 1L,
            sourceType = Attachment.SourceType.CHAT,
            sourceId = "room1",
            fileType = Attachment.FileType.IMAGE,
            storageKey = "chat/uuid_image.png"
        )

        every { attachmentRepository.findAll(null, 1, null, null, null) } returns listOf(attachment)
        every { memberRepository.findByIds(listOf(1L)) } returns listOf(m1)

        When("조회하면") {
            val result = adminService.getAttachments(null, 1)

            Then("이미지/비디오/오디오 형태의 첨부파일이 정상 반환되어야 한다") {
                result shouldHaveSize 1
                result[0].storageKey shouldBe "chat/uuid_image.png"
                result[0].fileType shouldBe "IMAGE"
                result[0].uploaderUsername shouldBe "user1"
            }
        }
    }

    Given("게시물 목록 조회 시 (삭제/블라인드 포함)") {
        val m1 = createMember(1L, "user1", "nick1")
        val post1 = Post(id = 10L, authorId = 1L, content = "Post 1")
        val post2 = Post(id = 11L, authorId = 1L, content = "Post 2").apply { delete() }

        every { postRepository.findAllForAdmin(null, 2) } returns listOf(post2, post1)
        every { memberRepository.findByIds(listOf(1L)) } returns listOf(m1)

        When("게시물 목록을 가져오면") {
            val result = adminService.getPosts(null, 2)

            Then("삭제 여부 및 블라인드 여부를 포함한 게시물 정보가 반환된다") {
                result shouldHaveSize 2
                result[0].id shouldBe 11L
                result[0].authorUsername shouldBe "user1"
                result[0].deleted shouldBe true
                result[0].blinded shouldBe false
                result[1].id shouldBe 10L
                result[1].deleted shouldBe false
            }
        }
    }

    Given("게시물 댓글 목록 조회 시 (삭제 포함)") {
        val m1 = createMember(1L, "user1", "nick1")
        val comment1 = Comment(id = 100L, postId = 10L, authorId = 1L, content = "Comment 1")
        val comment2 = Comment(id = 101L, postId = 10L, authorId = 1L, content = "Deleted Comment").apply { delete() }

        every { commentRepository.findByPostForAdmin(10L, null, 2) } returns listOf(comment2, comment1)
        every { memberRepository.findByIds(listOf(1L)) } returns listOf(m1)

        When("댓글 목록을 가져오면") {
            val result = adminService.getPostComments(10L, null, 2)

            Then("삭제 여부를 포함한 댓글 목록이 반환된다") {
                result shouldHaveSize 2
                result[0].id shouldBe 101L
                result[0].deleted shouldBe true
                result[1].id shouldBe 100L
                result[1].deleted shouldBe false
            }
        }
    }

    Given("신고 이력 조회 시") {
        val reporter = createMember(1L, "user1", "nick1")
        val reported = createMember(2L, "user2", "nick2")
        val report = Report(
            reporterId = 1L,
            reportedUserId = 2L,
            sourceType = Report.SourceType.ECHO_POST,
            sourceId = "10",
            reason = "Spam"
        )

        every { reportRepository.findAll(null, 1, Report.SourceType.ECHO_POST, null) } returns listOf(report)
        every { memberRepository.findByIds(listOf(1L, 2L)) } returns listOf(reporter, reported)

        When("신고 목록을 가져오면") {
            val result = adminService.getReports(null, 1, Report.SourceType.ECHO_POST)

            Then("신고자와 피신고자 정보가 매핑되어 반환된다") {
                result shouldHaveSize 1
                result[0].reporterUsername shouldBe "user1"
                result[0].reportedUsername shouldBe "user2"
                result[0].sourceType shouldBe "ECHO_POST"
                result[0].sourceId shouldBe "10"
                result[0].reason shouldBe "Spam"
            }
        }
    }
})
