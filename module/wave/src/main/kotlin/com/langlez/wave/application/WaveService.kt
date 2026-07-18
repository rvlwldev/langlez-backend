package com.langlez.wave.application

import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.wave.api.WaveResponse
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveRoomRepository
import com.langlez.wave.infrastructure.WaveViewerTracker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WaveService(
    private val waveRoomRepository: WaveRoomRepository,
    private val memberRepository: MemberRepository,
    private val relationshipRepository: RelationshipRepository,
    private val viewerTracker: WaveViewerTracker,
) {

    fun startLive(memberId: Long): WaveRoom {
        val broadcaster = memberRepository.findById(memberId)
            ?: throw LanglezException(404, "member.not-found")

        if (broadcaster.role == Member.Role.MEMBER) {
            throw LanglezException(403, "wave.premium-required")
        }

        val room = waveRoomRepository.save(WaveRoom(broadcasterId = memberId))

        val followerIds = relationshipRepository.findFollowers(memberId, null, FOLLOWER_NOTIFY_PAGE_SIZE)
            .map { it.followerId }
        logger.info("Wave room {} started by member {}; followers to notify: {}", room.id, memberId, followerIds)
        // TODO: Phase 6 Notification 모듈에서 FCM 발송 연동

        return room
    }

    @Transactional(readOnly = true)
    fun getRoom(roomId: Long): WaveRoom =
        waveRoomRepository.findById(roomId) ?: throw LanglezException(404, "wave.room-not-found")

    fun endLive(memberId: Long, roomId: Long): WaveRoom {
        val room = getRoom(roomId)
        if (room.broadcasterId != memberId) {
            throw LanglezException(403, "wave.not-broadcaster")
        }
        if (room.isEnded()) {
            throw LanglezException(409, "wave.already-ended")
        }
        room.end()
        return waveRoomRepository.save(room)
    }

    @Transactional(readOnly = true)
    fun getActiveRooms(cursor: Long?, size: Int): List<WaveRoom> {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        return waveRoomRepository.findActive(cursor, boundedSize)
    }

    @Transactional(readOnly = true)
    fun toRoomSummary(room: WaveRoom): WaveResponse.RoomSummary {
        val broadcaster = memberRepository.findById(room.broadcasterId)
            ?: throw LanglezException(404, "member.not-found")
        return WaveResponse.RoomSummary(
            id = room.id,
            broadcasterUsername = broadcaster.username,
            broadcasterNickname = broadcaster.nickname,
            startedAt = room.startedAt,
            endedAt = room.endedAt,
            viewerCount = viewerTracker.viewerCount(room.id)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WaveService::class.java)
        private const val MAX_PAGE_SIZE = 100
        private const val FOLLOWER_NOTIFY_PAGE_SIZE = 1000
    }
}
