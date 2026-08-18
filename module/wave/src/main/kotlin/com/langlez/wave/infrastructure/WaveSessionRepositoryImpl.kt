package com.langlez.wave.infrastructure

import com.langlez.wave.domain.WaveChat
import com.langlez.wave.domain.WaveSessionRepository
import org.redisson.api.RList
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Duration

/**
 * 사라지는 채팅의 링버퍼.
 *
 * 방마다 리스트 하나를 두고 넣을 때마다 뒤에서 [CAPACITY] 개만 남기고 잘라낸다(LTRIM).
 * 그래서 방이 아무리 길어져도 메모리는 방 수 × [CAPACITY] 로 묶인다.
 * 방이 비정상 종료돼(서버 강제 종료 등) `clear` 가 안 불려도 TTL 이 대신 치운다.
 */
@Repository
class WaveSessionRepositoryImpl(private val redisson: RedissonClient) : WaveSessionRepository {

    /**
     * 회원 id 를 문자열로 담는다. 공용 코덱(JsonJacksonCodec)은 final 타입에 타입 정보를 안 붙이고
     * 디코딩은 Object 로 해서, 작은 수가 Integer 로 되돌아온다. 그러면 `Set<Long>` 인 척하는
     * Integer 집합이 되어 `contains(1L)` 이 조용히 false 가 된다. (MemberOnlineTracker 와 같은 이유)
     */
    override fun join(roomId: Long, memberId: Long) {
        members(roomId).apply {
            add(memberId.toString())
            expire(TTL)
        }
    }

    override fun leave(roomId: Long, memberId: Long) {
        members(roomId).remove(memberId.toString())
    }

    override fun participants(roomId: Long): Set<Long> =
        members(roomId).readAll().mapNotNull(String::toLongOrNull).toSet()

    override fun isParticipant(roomId: Long, memberId: Long): Boolean =
        members(roomId).contains(memberId.toString())

    override fun appendChat(roomId: Long, chat: WaveChat) {
        val chats = chats(roomId)
        chats.add(chat)

        // add 와 trim 사이에 몇 개가 더 들어올 수 있다. 다음 전송이 다시 잘라내므로
        // 상한을 잠깐 몇 개 넘길 뿐이고, 그 정도를 막으려고 락을 걸 이유는 없다.
        val size = chats.size
        if (size > CAPACITY) chats.trim(size - CAPACITY, size - 1)

        chats.expire(TTL)
    }

    override fun recentChats(roomId: Long): List<WaveChat> = chats(roomId).readAll()

    override fun clear(roomId: Long) {
        chats(roomId).delete()
        members(roomId).delete()
    }

    private fun chats(roomId: Long): RList<WaveChat> = redisson.getList("wave:room:$roomId:chats")

    private fun members(roomId: Long): RSet<String> = redisson.getSet("wave:room:$roomId:participants")

    companion object {
        /** 늦게 들어온 사람이 흐름을 따라잡을 만큼만. 전체 기록을 보관하는 자리가 아니다. */
        const val CAPACITY = 200

        // 방이 하루 넘게 열려 있진 않다. 정상 종료되면 clear 가 먼저 지우고, TTL 은 그 실패에 대한 보험이다.
        private val TTL: Duration = Duration.ofHours(6)
    }
}
