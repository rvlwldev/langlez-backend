package com.langlez.chat.application

import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberRole
import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.redis.distributedLock.LockKey
import com.langlez.redis.ratelimit.DailyRateLimiter
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ChatRoomCreator(
    private val chatRoomRepository: ChatRoomRepository,
    private val memberRepository: MemberRepository,
    private val dailyRateLimiter: DailyRateLimiter,
) {

    @DistributedLock(prefix = "lock:chat-room:", leaseSecs = 5, retries = 20, waitMs = 100, transactional = true)
    fun getOrCreateRoom(requesterId: Long, @LockKey highId: Long, @LockKey lowId: Long): ChatRoom {
        // 1. Double-checked locking: re-check now that we hold the lock, because another thread may have
        //    created the room while we were waiting for the lock.
        val existing = chatRoomRepository.findByParticipants(lowId, highId)
        if (existing != null) return existing

        // 2. Only now do the rate limit check, using requesterId
        val member = memberRepository.findById(requesterId)
            ?: throw LanglezException(404, "member.not-found")
        val limit = when (member.role) {
            MemberRole.MEMBER -> 5
            MemberRole.PREMIUM, MemberRole.ADMIN -> 30
        }
        if (!dailyRateLimiter.tryConsume("chat:room:$requesterId", limit)) {
            throw LanglezException(429, "chat.room-daily-limit-exceeded")
        }

        // 3. Build and save the new ChatRoom exactly as before: participantIds = listOf(lowId, highId),
        //    readStatus = mutableMapOf(requesterId to Instant.now(), otherParticipantId to Instant.EPOCH)
        //    where otherParticipantId is whichever of lowId/highId is NOT requesterId.
        val otherParticipantId = if (lowId == requesterId) highId else lowId
        val now = Instant.now()
        val newRoom = ChatRoom(
            participantIds = listOf(lowId, highId),
            readStatus = mutableMapOf(
                requesterId to now,
                otherParticipantId to Instant.EPOCH
            )
        )
        return chatRoomRepository.save(newRoom)
    }
}
