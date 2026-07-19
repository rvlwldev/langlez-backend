package com.langlez.wavechat.application

import com.langlez.core.LanglezException
import com.langlez.wavechat.domain.WaveMessage
import com.langlez.wavechat.domain.WaveMessageRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WaveChatService(
    private val waveMessageRepository: WaveMessageRepository,
) {

    fun sendMessage(waveRoomId: Long, senderId: Long, content: String): WaveMessage {
        val message = WaveMessage(
            waveRoomId = waveRoomId,
            senderId = senderId,
            content = content,
        )
        return waveMessageRepository.save(message)
    }

    @Transactional(readOnly = true)
    fun getMessages(waveRoomId: Long, cursor: Long?, size: Int): List<WaveMessage> {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return waveMessageRepository.findByRoom(waveRoomId, cursor, boundedSize)
    }

    fun deleteMessage(waveRoomId: Long, senderId: Long, messageId: Long) {
        val message = waveMessageRepository.findById(messageId)
            ?: throw LanglezException(404, "wavechat.message-not-found")

        if (message.waveRoomId != waveRoomId) {
            throw LanglezException(404, "wavechat.message-not-found")
        }

        if (message.senderId != senderId) {
            throw LanglezException(403, "wavechat.forbidden")
        }

        waveMessageRepository.markDeleted(messageId)
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
