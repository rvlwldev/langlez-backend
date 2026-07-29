package com.langlez.attachment.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.core.MessageConsumer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AttachmentEventListener(
    private val attachmentRepository: AttachmentRepository,
    private val mapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @MessageConsumer(topics = ["topic-echo_post"], group = "attachment-service")
    fun onEchoAttachmentsUploaded(payload: String) {
        val attachments = runCatching {
            val event = mapper.readValue(payload, EchoAttachmentsUploadedEvent::class.java)
            event.attachments.map {
                Attachment(
                    uploaderId = event.uploaderId,
                    sourceType = Attachment.SourceType.ECHO,
                    sourceId = event.postId,
                    fileType = Attachment.FileType.valueOf(it.fileType),
                    storageKey = it.storageKey,
                )
            }
        }.getOrElse { logger.warn("Failed to parse echo attachment event: {}", it.message); return }

        attachmentRepository.saveAll(attachments)
    }

    @MessageConsumer(topics = ["topic-chat_message"], group = "attachment-service")
    fun onChatAttachmentUploaded(payload: String) {
        val attachments = runCatching {
            val event = mapper.readValue(payload, ChatAttachmentUploadedEvent::class.java)
            listOf(
                Attachment(
                    uploaderId = event.uploaderId,
                    sourceType = Attachment.SourceType.CHAT,
                    sourceId = event.roomId,
                    fileType = Attachment.FileType.valueOf(event.fileType),
                    storageKey = event.storageKey,
                ),
            )
        }.getOrElse { logger.warn("Failed to parse chat attachment event: {}", it.message); return }

        attachmentRepository.saveAll(attachments)
    }
}
