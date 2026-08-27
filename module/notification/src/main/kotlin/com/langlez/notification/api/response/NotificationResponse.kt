package com.langlez.notification.api.response

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.notification.domain.Notification
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * 알림함 응답.
 *
 * 실시간 인앱 알림의 `NotificationView` 를 재사용하지 않는다. 알림함은 읽음 여부가 핵심인데
 * 브로드캐스트 페이로드는 방금 만든 알림이라 항상 안 읽음이라 그 필드가 없고, 그쪽에 필드를 더하면
 * HTTP 응답을 고칠 때마다 WebSocket 페이로드가 조용히 같이 바뀐다.
 *
 * `data` 는 DB 에 JSON 문자열로 들어 있다. 문자열 그대로 내보내면 클라이언트가 실시간 알림
 * (`NotificationView.data` 는 맵이다)과 HTTP 응답을 다른 코드로 파싱해야 해서 여기서 맵으로 되돌린다.
 */
data class NotificationResponse(
    @field:Schema(description = "알림 id. 목록 커서로 그대로 쓴다") val id: Long,
    @field:Schema(description = "알림 종류", example = "CHAT_MESSAGE") val type: String,
    @field:Schema(description = "제목. i18n 메시지 키라 클라이언트가 사용자 언어로 그린다") val title: String,
    @field:Schema(description = "본문. 팔로우 알림처럼 동적 본문이 없으면 빈 문자열") val body: String,
    @field:Schema(description = "화면 이동에 쓸 부가 데이터") val data: Map<String, String>,
    @field:Schema(description = "읽음 여부") val read: Boolean,
    @field:Schema(description = "수신 시각") val createdAt: Instant,
) {
    constructor(notification: Notification, mapper: ObjectMapper) : this(
        id = notification.id,
        type = notification.type,
        title = notification.title,
        body = notification.body,
        data = notification.data?.let { mapper.readValue(it, DATA_TYPE) } ?: emptyMap(),
        read = notification.read,
        createdAt = notification.createdAt,
    )

    private companion object {
        val DATA_TYPE = object : TypeReference<Map<String, String>>() {}
    }
}
