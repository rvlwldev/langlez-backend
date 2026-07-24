package com.langlez.wave.infrastructure

import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.redis.distributedLock.LockKey
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** 방(roomId)의 현재 시청자 목록을 Redis Set(`wave:room:{roomId}:viewers`)으로 관리한다. */
@Component
class WaveViewerTracker(
    private val redissonClient: RedissonClient,
) {
    /**
     * STOMP 세션이 어느 방의 시청자로 등록되었는지 기억해두었다가, DISCONNECT/UNSUBSCRIBE 시 해당 방에서 제거하기 위함.
     * Note: 현재 인스턴스 로컬 ConcurrentHashMap을 사용하므로 단일 인스턴스 환경을 전제로 함.
     * 수평 확장(Multi-instance) 환경에서는 세션-방 매핑을 Redis(e.g., wave:session:{sessionId})에 저장하여 시청자 누수를 방지해야 한다.
     */
    private val sessionRooms = ConcurrentHashMap<String, Pair<Long, Long>>() // sessionId -> (roomId, memberId)

    fun addViewer(roomId: Long, memberId: Long) {
        redissonClient.getSet<Long>(viewersKey(roomId)).add(memberId)
    }

    fun removeViewer(roomId: Long, memberId: Long) {
        redissonClient.getSet<Long>(viewersKey(roomId)).remove(memberId)
    }

    fun viewerCount(roomId: Long): Long =
        redissonClient.getSet<Long>(viewersKey(roomId)).size.toLong()

    fun trackSession(sessionId: String, roomId: Long, memberId: Long) {
        sessionRooms[sessionId] = roomId to memberId
    }

    @DistributedLock(prefix = "lock:wave-join:", ttl = 5, retries = 20, wait = 100)
    fun addViewerIfAllowed(@LockKey roomId: Long, memberId: Long, maxParticipants: Int): Boolean {
        if (isViewer(roomId, memberId)) return true
        if (viewerCount(roomId) >= maxParticipants) return false
        addViewer(roomId, memberId)
        return true
    }

    fun leave(sessionId: String) {
        val (roomId, memberId) = sessionRooms.remove(sessionId) ?: return
        val hasOtherSessionInRoom = sessionRooms.values.any { it.first == roomId && it.second == memberId }
        if (!hasOtherSessionInRoom) {
            removeViewer(roomId, memberId)
        }
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
