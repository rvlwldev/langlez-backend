package com.langlez.chat.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * 채팅 메시지.
 *
 * 첨부를 별도 컬렉션으로 빼지 않고 문서에 임베드한다. 메시지 1건과 그 첨부는 항상 함께 읽고 함께 지워지므로,
 * 나눠 두면 목록 조회마다 조인(또는 두 번째 왕복)이 붙기만 한다.
 *
 * 정렬·커서는 `createdAt` 이 아니라 `seq`(방별 단조 증가)다. 인스턴스마다 시계가 어긋나면
 * `createdAt` 기준 순서가 조용히 뒤집힌다.
 */
@Document(collection = "chat_messages")
@CompoundIndex(name = "IDX_CHAT_MESSAGE_ROOM_SEQ", def = "{ 'roomId': 1, 'seq': -1 }")
@CompoundIndex(name = "IDX_CHAT_MESSAGE_ROOM_CREATED", def = "{ 'roomId': 1, 'createdAt': -1 }")
// 대사 스케줄러는 방을 가리지 않고 최근 창의 메시지를 훑는다. roomId 가 앞에 붙은 인덱스로는 못 타서
// 5분마다 컬렉션 전체 스캔이 된다. 키가 단조 증가라 쓰기 비용은 오른쪽 끝 삽입 한 번뿐이다.
@CompoundIndex(name = "IDX_CHAT_MESSAGE_CREATED", def = "{ 'createdAt': 1 }")
class ChatMessage(
    val roomId: Long,
    val senderId: Long,
    val seq: Long,
    val type: Type,
    val content: String? = null,
    val files: List<Attachment> = emptyList(),
    val createdAt: Instant = Instant.now(),
) {
    @Id
    var id: String? = null

    // 삭제는 "모두에게 삭제"다. 문서를 지우지 않아야 상대 화면에서 [삭제된 메시지] 로 자리를 남길 수 있다.
    var deletedAt: Instant? = null
        private set

    /**
     * 알림 발행 여부. 별도 아웃박스 컬렉션 대신 이 플래그를 쓴다.
     * 아웃박스를 두면 메시지마다 문서가 하나 더 생겨 가장 빈번한 쓰기가 두 배가 되는데,
     * 단일 문서 쓰기는 Mongo 에서 원자적이라 플래그만으로 같은 보장을 얻는다.
     * 미발행 문서만 훑으므로 부분 인덱스로 인덱스 크기를 발행 대기분으로 제한한다.
     */
    @Indexed(name = "IDX_CHAT_MESSAGE_UNPUBLISHED", partialFilter = "{ 'published': false }")
    var published: Boolean = false
        private set

    fun delete(requesterId: Long, now: Instant = Instant.now()) {
        require(senderId == requesterId) { "chat.message.not-owner" }
        require(deletedAt == null) { "chat.message.already-deleted" }
        deletedAt = now
    }

    fun markPublished() {
        published = true
    }

    /**
     * 방 목록·알림에 보여줄 한 줄 요약.
     *
     * 사진·음성은 보여줄 본문이 없어 타입만 남기고 문구는 클라이언트가 현지화한다.
     * 서비스와 발행 폴러가 같은 값을 써야 해서 여기 둔다 — 양쪽에 따로 두면 조용히 어긋난다.
     */
    fun preview(): String = content?.takeIf { it.isNotBlank() } ?: "[$type]"

    fun isDeleted(): Boolean = deletedAt != null

    /** 앨범은 보낸 순서가 곧 화면 순서다. 목록 인덱스를 그대로 sequence 로 박는다. */
    class Attachment(val url: String, val sequence: Int)

    enum class Type { TEXT, IMAGE, VIDEO, AUDIO }
}
