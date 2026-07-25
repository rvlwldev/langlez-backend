package com.langlez.wavechat.domain

interface WaveMessageRepository {
    fun save(message: WaveMessage): WaveMessage
    fun findByRoom(waveRoomId: Long, cursor: Long?, size: Int): List<WaveMessage>
    fun findById(id: Long): WaveMessage?
}
