package com.langlez.wave.application

import com.langlez.core.MessageBroadcaster
import com.langlez.exception.LanglezException
import com.langlez.wave.domain.WaveChat
import com.langlez.wave.domain.WaveRepository
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveSessionRepository
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 단체 음성방 유스케이스.
 *
 * 방 안의 텍스트 채팅은 저장하지 않는다 — 방이 끝나면 대화도 끝난다. 링버퍼(레디스)에 최근 몇 개만
 * 남기고 실시간 전달은 `MessageBroadcaster` 로 한다. `SimpMessagingTemplate` 을 직접 부르면
 * 인스턴스가 여러 대일 때 다른 서버에 붙은 사람이 아무 오류 없이 조용히 못 받는다.
 *
 * 방을 건드리는 모든 동작은 참여자인지부터 확인한다. roomId 는 클라이언트가 그대로 보내는 값이라
 * 한 군데라도 빼면 남의 방 대화가 새어 나간다(IDOR).
 */
@Service
class WaveService(
    private val repo: WaveRepository,
    private val sessions: WaveSessionRepository,
    private val broadcaster: MessageBroadcaster,
) {

    /**
     * 방 개설. 연 사람이 곧 첫 참여자다.
     *
     * `repo.save` 가 자기 트랜잭션을 갖고, 참여자 등록은 레디스 쓰기다.
     * 둘을 한 트랜잭션으로 묶으면 DB 커넥션을 쥔 채 레디스를 기다리게 되어 일부러 걸지 않았다.
     */
    fun createRoom(memberId: Long, title: String, maxParticipants: Int): WaveRoom {
        val room = try {
            WaveRoom(broadcasterId = memberId, title = title, maxParticipants = maxParticipants)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }

        return repo.save(room).also { sessions.join(it.id, memberId) }
    }

    @Transactional(readOnly = true)
    fun listOpenRooms(size: Int, cursor: Long?): List<WaveRoom> = repo.findAllOpen(size, cursor)

    /**
     * 입장.
     *
     * 정원 검사와 등록 사이가 갈라지면 두 사람이 동시에 마지막 자리에 들어간다.
     * 인스턴스가 여러 대라 JVM 락으로는 못 막는다.
     *
     * 그렇다고 `@DistributedLock` 으로 감싸지 않는다. 그 어노테이션은 기본값이
     * `waitMs = 0, retries = 0, throwOnFailure = false` 라 락을 못 잡으면 **본문을 통째로 건너뛰고
     * 정상 반환한다.** `join` 은 `Unit` 이라 그 스킵이 컨트롤러의 204 로 나가고, 사용자는 들어갔다고
     * 믿지만 참여자 집합에 없어 이후 구독·채팅이 전부 403 이 된다. 대기·재시도를 주거나
     * `throwOnFailure = true` 로 바꿔도 "락을 못 잡았다"는 사용자 눈에 원인 불명의 실패로 남는다.
     *
     * 원자성이 필요한 구간이 "정원 검사 + 등록" 하나뿐이라 락 대신 레디스가 직접 보장하게 한다
     * ([WaveSessionRepository.joinIfNotFull]). 락도, 대기도, 조용한 스킵도 없어진다.
     *
     * 감수하는 창: 방이 열려 있는지 본 뒤 등록하기까지 사이에 방장이 방을 닫을 수 있다.
     * 그러면 이미 닫힌 방의 참여자 집합에 한 명이 남는데, TTL(6시간)이 치운다.
     * 이 창은 락이 있던 시절에도 있었다 — `end`/`leave` 는 같은 락을 잡지 않았다.
     */
    fun join(roomId: Long, memberId: Long) {
        val room = openRoomOrThrow(roomId)

        // 재입장(네트워크 끊김 후 복귀)은 joinIfNotFull 이 정원과 무관하게 통과시킨다.
        // 여기서 막으면 끊긴 사람이 자기가 차지한 자리 때문에 다시 못 들어온다.
        if (!sessions.joinIfNotFull(roomId, memberId, room.maxParticipants)) {
            throw LanglezException(CONFLICT, "wave.room.full")
        }
    }

    /** 퇴장. 마지막 사람이 나가면 방을 닫는다 — 안 닫으면 아무도 없는 방이 목록에 영원히 남는다. */
    fun leave(roomId: Long, memberId: Long) {
        sessions.leave(roomId, memberId)

        if (sessions.participants(roomId).isEmpty()) close(roomId)
    }

    /** 종료는 방장만. 방과 함께 대화도 사라진다. */
    fun end(roomId: Long, memberId: Long) {
        val room = openRoomOrThrow(roomId)
        if (room.broadcasterId != memberId) throw LanglezException(FORBIDDEN, "wave.room.not-host")

        close(roomId)
    }

    fun chat(roomId: Long, memberId: Long, content: String): WaveChat {
        if (content.isBlank()) throw LanglezException(BAD_REQUEST, "wave.chat.empty")

        openRoomOrThrow(roomId)
        participantOrThrow(roomId, memberId)

        return WaveChat(roomId = roomId, senderId = memberId, content = content)
            .also { sessions.appendChat(roomId, it) }
            .also { broadcaster.broadcast(topic(roomId), it) }
    }

    /** 늦게 들어온 사람이 흐름을 따라잡는 용도다. 참여자가 아니면 남의 대화이므로 막는다. */
    fun recentChats(roomId: Long, memberId: Long): List<WaveChat> {
        participantOrThrow(roomId, memberId)

        return sessions.recentChats(roomId)
    }

    private fun close(roomId: Long) {
        repo.find(roomId)
            ?.takeUnless { it.isEnded() }
            ?.apply { end() }
            ?.also(repo::save)

        sessions.clear(roomId)
    }

    private fun openRoomOrThrow(roomId: Long): WaveRoom {
        val room = repo.find(roomId) ?: throw LanglezException(NOT_FOUND, "wave.room.not-found")
        if (room.isEnded()) throw LanglezException(CONFLICT, "wave.room.already-ended")

        return room
    }

    private fun participantOrThrow(roomId: Long, memberId: Long) {
        if (!sessions.isParticipant(roomId, memberId)) throw LanglezException(FORBIDDEN, "wave.room.forbidden")
    }

    private fun topic(roomId: Long) = "/topic/wave/$roomId/chat"
}
