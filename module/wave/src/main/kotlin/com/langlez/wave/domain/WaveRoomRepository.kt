package com.langlez.wave.domain

interface WaveRoomRepository {
    fun save(room: WaveRoom): WaveRoom
    fun findById(id: Long): WaveRoom?
    fun findActive(cursor: Long?, size: Int): List<WaveRoom>
}
