package com.langlez.wave.infrastructure

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** 방(roomId)의 현재 시청자 목록을 Redis Set(`wave:room:{roomId}:viewers`)으로 관리한다. */
@Component
class WaveViewerTracker(
    private val redissonClient: RedissonClient,
) {
    // STOMP 세션이 어느 방의 시청자로 등록되었는지 기억해두었다가, DISCONNECT/UNSUBSCRIBE 시 해당 방에서 제거하기 위함.
    private val sessionRooms = ConcurrentHashMap<String, Pair<Long, Long>>() // sessionId -> (roomId, memberId)

    fun addViewer(roomId: Long, memberId: Long) {
        redissonClient.getSet<Long>(viewersKey(roomId)).add(memberId)
    }

    fun removeViewer(roomId: Long, memberId: Long) {
        redissonClient.getSet<Long>(viewersKey(roomId)).remove(memberId)
    }

    fun viewerCount(roomId: Long): Long =
        redissonClient.getSet<Long>(viewersKey(roomId)).size.toLong()

    fun join(sessionId: String, roomId: Long, memberId: Long) {
        addViewer(roomId, memberId)
        trackSession(sessionId, roomId, memberId)
    }

    fun trackSession(sessionId: String, roomId: Long, memberId: Long) {
        sessionRooms[sessionId] = roomId to memberId
    }

    fun addViewerIfAllowed(roomId: Long, memberId: Long, maxParticipants: Int): Boolean {
        val script = """
            if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
                return 1
            end
            if redis.call('SCARD', KEYS[1]) >= tonumber(ARGV[2]) then
                return 0
            end
            redis.call('SADD', KEYS[1], ARGV[1])
            return 1
        """.trimIndent()

        val result = redissonClient.script.eval<Long>(
            org.redisson.api.RScript.Mode.READ_WRITE,
            script,
            org.redisson.api.RScript.ReturnType.INTEGER,
            listOf(viewersKey(roomId)),
            memberId,
            maxParticipants
        )
        return result == 1L
    }

    fun leave(sessionId: String) {
        val (roomId, memberId) = sessionRooms.remove(sessionId) ?: return
        removeViewer(roomId, memberId)
    }

    fun isViewer(roomId: Long, memberId: Long): Boolean =
        redissonClient.getSet<Long>(viewersKey(roomId)).contains(memberId)

    fun banUser(roomId: Long, memberId: Long) {
        redissonClient.getSet<Long>(bannedKey(roomId)).add(memberId)
    }

    fun isBanned(roomId: Long, memberId: Long): Boolean =
        redissonClient.getSet<Long>(bannedKey(roomId)).contains(memberId)

    fun kickUser(roomId: Long, memberId: Long) {
        banUser(roomId, memberId)
        removeViewer(roomId, memberId)
    }

    private fun viewersKey(roomId: Long) = "wave:room:$roomId:viewers"
    private fun bannedKey(roomId: Long) = "wave:room:$roomId:banned"
}
