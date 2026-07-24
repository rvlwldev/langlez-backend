package com.langlez.wave.application

import com.langlez.core.LanglezException
import com.langlez.core.Notificator
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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CompletableFuture

@Service
@Transactional
class WaveService(
    private val waveRoomRepository: WaveRoomRepository,
    private val memberRepository: MemberRepository,
    private val relationshipRepository: RelationshipRepository,
    private val viewerTracker: WaveViewerTracker,
    private val notificator: Notificator,
    private val broadcaster: WaveBroadcaster,
) {

    fun startLive(memberId: Long, title: String, maxParticipants: Int): WaveRoom {
        val broadcaster = memberRepository.findById(memberId)
            ?: throw LanglezException(404, "member.not-found")

        if (broadcaster.role == Member.Role.MEMBER) {
            throw LanglezException(403, "wave.premium-required")
        }

        val room = waveRoomRepository.save(
            WaveRoom(
                broadcasterId = memberId,
                title = title,
                maxParticipants = maxParticipants
            )
        )

        val followerIds = relationshipRepository.findFollowers(memberId, null, FOLLOWER_NOTIFY_PAGE_SIZE)
            .map { it.followerId }
        if (followerIds.isNotEmpty()) {
            val broadcasterNickname = broadcaster.nickname
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                    override fun afterCommit() {
                        CompletableFuture.runAsync {
                            sendFollowerNotifications(followerIds, broadcasterNickname)
                        }
                    }
                })
            } else {
                CompletableFuture.runAsync {
                    sendFollowerNotifications(followerIds, broadcasterNickname)
                }
            }
        }

        return room
    }

    private fun sendFollowerNotifications(followerIds: List<Long>, broadcasterNickname: String) {
        followerIds.forEach { followerId ->
            try {
                notificator.notify(
                    followerId,
                    "wave.live-started",
                    "${broadcasterNickname}님이 라이브를 시작했어요",
                    "지금 바로 들어와보세요!"
                )
            } catch (e: Exception) {
                logger.error("Failed to send wave live notification to follower $followerId", e)
            }
        }
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

    fun updateTitle(memberId: Long, roomId: Long, title: String): WaveRoom {
        val room = getRoom(roomId)
        if (room.broadcasterId != memberId) {
            throw LanglezException(403, "wave.not-broadcaster")
        }
        room.updateTitle(title)
        return waveRoomRepository.save(room)
    }

    @Transactional(readOnly = true)
    fun muteMember(memberId: Long, roomId: Long, targetMemberId: Long) {
        val room = getRoom(roomId)
        if (room.broadcasterId != memberId) {
            throw LanglezException(403, "wave.not-broadcaster")
        }
        if (room.isEnded()) {
            throw LanglezException(409, "wave.already-ended")
        }
        broadcaster.sendMuteToUser(targetMemberId, WaveMutePayload(roomId))
    }

    @Transactional(readOnly = true)
    fun kickMember(memberId: Long, roomId: Long, targetMemberId: Long) {
        val room = getRoom(roomId)
        if (room.broadcasterId != memberId) {
            throw LanglezException(403, "wave.not-broadcaster")
        }
        if (room.isEnded()) {
            throw LanglezException(409, "wave.already-ended")
        }
        viewerTracker.kickUser(roomId, targetMemberId)
        broadcaster.sendKickToUser(targetMemberId, WaveKickPayload(roomId))
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
            title = room.title,
            maxParticipants = room.maxParticipants,
            startedAt = room.startedAt,
            endedAt = room.endedAt,
            viewerCount = viewerTracker.viewerCount(room.id)
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WaveService::class.java)
        const val MAX_PAGE_SIZE = 100
        private const val FOLLOWER_NOTIFY_PAGE_SIZE = 1000
    }
}
