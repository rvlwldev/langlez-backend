package com.langlez.chat.application

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomMember
import com.langlez.chat.domain.ChatRoomSummary
import com.langlez.relationship.contract.BlockQuery
import com.langlez.core.MessageBroadcaster
import com.langlez.attachment.contract.Storage
import com.langlez.chat.contract.ChatUserReportedEvent
import com.langlez.exception.LanglezException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * 1:1 채팅 유스케이스.
 *
 * 방을 건드리는 모든 동작은 참여자인지부터 확인한다. roomId 는 클라이언트가 그대로 보내는 값이라
 * 검사를 한 군데라도 빼면 남의 대화가 통째로 새어 나간다(IDOR).
 *
 * 저장소가 둘이다 — 메시지 본문은 Mongo(`messages`), 방·참여자는 Postgres(`repo`).
 */
@Service
class ChatService(
    private val repo: ChatRepository,
    private val messages: ChatMessageRepository,
    private val blocks: BlockQuery,
    private val storage: Storage,
    private val broadcaster: MessageBroadcaster,
    private val publisher: ApplicationEventPublisher,
    private val tx: TransactionTemplate,
) {

    /**
     * 1:1 방 조회 또는 생성.
     *
     * 차단 판정은 `relationship-api` 포트라 트랜잭션 밖에서 먼저 끝낸다 — `send` 와 같은 모양이다.
     * 판정과 생성 사이에 상대가 나를 차단하면 빈 방이 하나 생기지만, 그 방으로 보내는 메시지는
     * `send` 가 다시 차단을 보고 막는다. 남는 건 대화가 없는 방 한 개다.
     */
    fun getOrCreateRoom(memberId: Long, partnerId: Long): ChatRoom {
        if (memberId == partnerId) throw LanglezException(BAD_REQUEST, "chat.self-room")
        if (blocks.isBlockedBetween(memberId, partnerId)) throw LanglezException(FORBIDDEN, "chat.blocked")

        // 있으면 재사용한다. 매번 만들면 같은 상대와 방이 계속 늘어나고 대화가 갈라진다.
        return tx.execute { repo.findRoomBetween(memberId, partnerId) ?: repo.createRoom(memberId, partnerId) }!!
    }

    /**
     * 내 방 목록.
     *
     * 저장소는 나간 방도 그대로 돌려준다 — 재입장 정책이라 상대가 다시 보내면 되살아나야 하고,
     * 그 판단(leftAt 이 아직 살아 있는가)은 여기서 한다.
     */
    @Transactional(readOnly = true)
    fun listRooms(memberId: Long, size: Int, cursor: Instant?): List<ChatRoomSummary> =
        repo.findRoomSummaries(memberId, size, cursor)
            // ponytail: 페이지 크기만큼 참여자 단건 조회가 붙는다(유니크 인덱스라 건당 비용은 작다).
            // 목록이 느려지면 findRoomSummaries 쿼리 자체에 leftAt 조건을 넣는 쪽으로 올린다.
            .filter { repo.findParticipant(it.room.id, memberId)?.hasLeft() != true }

    /** 참여 여부만 Postgres 에서 확인하고 본문은 Mongo 에서 읽는다. 첨부가 임베드라 조회는 한 번뿐이다. */
    @Transactional(readOnly = true)
    fun listMessages(memberId: Long, roomId: Long, size: Int, cursor: Long?): List<ChatMessageView> {
        participantOrThrow(roomId, memberId)

        // 나갔던 사람도 이전 대화를 그대로 본다(재입장 정책). 그래서 leftAt 으로 자르지 않는다.
        return messages.findByRoom(roomId, size, cursor).map(ChatMessageView::of)
    }

    /**
     * 메시지 전송.
     *
     * 순서가 중요하다 — 첨부 확정(블로킹 I/O) → **Mongo 저장** → Postgres 방 메타 갱신 → 브로드캐스트.
     * 두 저장소를 가로지르는 원자성은 없으므로 실패했을 때 덜 나쁜 쪽을 먼저 쓴다.
     * Postgres 가 먼저면 "목록엔 보이는데 열면 없는 메시지"가 되고, Mongo 가 먼저면 최악이
     * "방 목록에 안 뜨는 고아 메시지"라 대사 스케줄러가 나중에 복구할 수 있다.
     *
     * 알림 발행 여부는 여기서 정하지 않는다. `published = false` 로 남겨 두면 발행기가 가장 최신 상태를
     * 보고 결정한다 — 전송 시점에 정하면 그 사이 상대가 방에 들어오고 나가는 변화를 못 잡는다.
     */
    fun send(
        memberId: Long,
        roomId: Long,
        type: ChatMessage.Type,
        content: String?,
        keys: List<String>,
    ): ChatMessageView {
        if (content.isNullOrBlank() && keys.isEmpty()) throw LanglezException(BAD_REQUEST, "chat.message.empty")

        val partner = partnerOrThrow(roomId, memberId)
        if (blocks.isBlockedBetween(memberId, partner.memberId)) throw LanglezException(FORBIDDEN, "chat.blocked")

        // storage.attach 는 S3 확인이 걸린 블로킹 I/O 다. DB 커넥션을 쥔 채 기다리지 않도록 먼저 끝낸다.
        val urls = keys.map { storage.attach(it) }

        val message = messages.save(
            ChatMessage(
                roomId = roomId,
                senderId = memberId,
                seq = messages.nextSeq(roomId),
                type = type,
                content = content,
                files = urls.mapIndexed { i, url -> ChatMessage.Attachment(url, i) },
            )
        )

        // 여기서 실패해도 메시지는 Mongo 에 남는다. 대사 스케줄러가 프리뷰·카운터를 다시 맞춘다.
        tx.execute {
            val room = repo.findRoom(roomId) ?: throw LanglezException(NOT_FOUND, "chat.room.not-found")
            room.onMessage(message.preview(), message.createdAt)

            // 안 읽은 수는 DB 에서 더한다. 엔티티에 읽고-쓰기로 올리면
            // 같은 상대에게 연속 전송할 때 증가가 유실된다.
            repo.increaseUnread(roomId, partner.memberId)

            // 재입장 정책: 나간 상대도 새 메시지가 오면 방이 되살아난다(이전 대화 포함).
            if (partner.hasLeft()) repo.saveParticipant(partner.apply { rejoin() })
        }

        // 실시간 전달은 방 메타까지 커밋된 뒤에. 롤백된 상태를 상대 화면에 띄우면 되돌릴 방법이 없다.
        return ChatMessageView.of(message).also { broadcaster.broadcast(topic(roomId), it) }
    }

    /**
     * 첨부 업로드 URL 발급.
     *
     * key 를 함께 내려줘야 클라이언트가 서명 붙은 PUT URL 대신 key 로 전송할 수 있다.
     * URL 을 그대로 받으면 외부 주소를 첨부로 심을 수 있다.
     * contentType 은 믿지 않는다 — 채팅 첨부는 사진·영상·음성뿐이다.
     */
    fun presignUpload(memberId: Long, filename: String, contentType: String): Storage.PresignedResult {
        val type = Storage.Type.entries.firstOrNull { contentType.startsWith("${it.name.lowercase()}/") }
            ?: throw LanglezException(BAD_REQUEST, "chat.attachment.unsupported-type")

        return storage.presign(memberId, "chat", type, filename)
    }

    fun markRead(memberId: Long, roomId: Long, at: Instant = Instant.now()) {
        participantOrThrow(roomId, memberId).apply { markRead(at) }
            .also(repo::saveParticipant)

        // 저장만 하면 상대는 새로고침해야 읽음을 안다. 실시간으로 밀어준다.
        broadcaster.broadcast(topic(roomId), ChatReadEvent(roomId, memberId, at))
    }

    /** 나가기는 참여 행을 지우지 않는다. 지우면 재입장했을 때 읽음 위치가 사라진다. */
    @Transactional
    fun leaveRoom(memberId: Long, roomId: Long) {
        participantOrThrow(roomId, memberId).apply { leave() }
            .also(repo::saveParticipant)
    }

    /**
     * 모두에게 삭제. 문서는 남기고 내용만 가려 상대 화면에 [삭제된 메시지] 자리를 남긴다.
     * Mongo 단일 문서 쓰기 하나뿐이라 트랜잭션으로 감싸지 않는다.
     */
    fun deleteMessage(memberId: Long, messageId: String) {
        val message = messages.find(messageId) ?: throw LanglezException(NOT_FOUND, "chat.message.not-found")

        try {
            message.delete(memberId)
        } catch (e: IllegalArgumentException) {
            // 남의 메시지를 지우려는 건 권한 문제(403), 이미 지운 걸 또 지우는 건 잘못된 요청(400)이다.
            val status = if (e.message == "chat.message.not-owner") FORBIDDEN else BAD_REQUEST
            throw LanglezException(status, e.message, e)
        }

        messages.save(message)

        broadcaster.broadcast(topic(message.roomId), ChatMessageView.of(message))
    }

    /** 신고 접수 사실만 알린다. Report 저장은 relationship 모듈이 카프카로 받아서 한다. */
    @Transactional
    fun report(memberId: Long, roomId: Long, reason: String, triggerMessageId: String?) {
        val partner = partnerOrThrow(roomId, memberId)

        publisher.publishEvent(
            ChatUserReportedEvent(roomId, memberId, partner.memberId, reason, triggerMessageId)
        )
    }

    private fun participantOrThrow(roomId: Long, memberId: Long): ChatRoomMember =
        repo.findParticipant(roomId, memberId) ?: throw LanglezException(FORBIDDEN, "chat.room.forbidden")

    /** 1:1 방이라 나 아닌 참여자가 곧 상대다. 참여자 확인과 상대 조회를 한 번의 조회로 끝낸다. */
    private fun partnerOrThrow(roomId: Long, memberId: Long): ChatRoomMember {
        val participants = repo.findParticipants(roomId)

        if (participants.none { it.memberId == memberId }) throw LanglezException(FORBIDDEN, "chat.room.forbidden")

        return participants.firstOrNull { it.memberId != memberId }
            ?: throw LanglezException(NOT_FOUND, "chat.room.not-found")
    }

    private fun topic(roomId: Long) = "/topic/chat/room/$roomId"
}

/**
 * 메시지 응답·브로드캐스트 페이로드.
 *
 * 엔티티를 그대로 내보내면 삭제된 메시지의 본문과 첨부까지 실려 나간다. 여기서 한 번 걸러 만든다.
 */
data class ChatMessageView(
    val id: String,
    val seq: Long,
    val roomId: Long,
    val senderId: Long,
    val type: ChatMessage.Type,
    val content: String?,
    val fileUrls: List<String>,
    val createdAt: Instant,
    val deleted: Boolean,
) {
    companion object {
        fun of(message: ChatMessage) = ChatMessageView(
            // 저장을 거친 문서만 뷰가 된다. id 가 없다면 저장소 계약이 깨진 것이라 감춰선 안 된다.
            id = requireNotNull(message.id),
            seq = message.seq,
            roomId = message.roomId,
            senderId = message.senderId,
            type = message.type,
            content = message.content.takeUnless { message.isDeleted() },
            fileUrls = message.files
                .takeUnless { message.isDeleted() }
                .orEmpty()
                .sortedBy(ChatMessage.Attachment::sequence)
                .map(ChatMessage.Attachment::url),
            createdAt = message.createdAt,
            deleted = message.isDeleted(),
        )
    }
}

/**
 * 읽음 브로드캐스트 페이로드.
 *
 * `com.langlez.` 패키지 아래여야 한다 — 레디스 코덱이 이 접두사에만 타입 정보를 남긴다.
 */
data class ChatReadEvent(val roomId: Long, val memberId: Long, val readAt: Instant)
