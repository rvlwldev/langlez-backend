package com.langlez.wave.infrastructure

import com.langlez.wave.domain.WaveChat
import com.langlez.wave.domain.WaveSessionRepository
import org.redisson.api.RList
import org.redisson.api.RScript
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
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
     * 정원 검사와 등록을 한 번에 끝낸다.
     *
     * 레디스는 스크립트를 통째로 원자 실행하므로 SCARD 와 SADD 사이에 남이 끼어들 수 없다.
     * 재입장(네트워크 끊김 후 복귀)은 정원과 무관하게 통과시킨다 — 여기서 막으면 끊긴 사람이
     * 자기가 차지한 자리 때문에 다시 못 들어온다.
     *
     * TTL 은 넣을 때마다 다시 건다. 방이 살아 있는 동안은 참여자 집합도 함께 살아 있어야 한다.
     */
    private val joinIfNotFullScript = """
        if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 0 then
            if redis.call('SCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 0 end
            redis.call('SADD', KEYS[1], ARGV[1])
        end
        redis.call('PEXPIRE', KEYS[1], ARGV[3])
        return 1
    """.trimIndent()

    override fun join(roomId: Long, memberId: Long) {
        members(roomId).apply {
            add(memberId.toString())
            expire(TTL)
        }
    }

    override fun joinIfNotFull(roomId: Long, memberId: Long, maxParticipants: Int): Boolean {
        val joined: Long = redisson.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,
            joinIfNotFullScript,
            RScript.ReturnType.INTEGER,
            listOf(membersKey(roomId)),
            memberId.toString(),
            maxParticipants.toString(),
            TTL.toMillis().toString(),
        )

        return joined == 1L
    }

    override fun leave(roomId: Long, memberId: Long) {
        members(roomId).remove(memberId.toString())
    }

    override fun participants(roomId: Long): Set<Long> =
        members(roomId).readAll().mapNotNull(String::toLongOrNull).toSet()

    override fun participantCount(roomId: Long): Int = members(roomId).size

    override fun isParticipant(roomId: Long, memberId: Long): Boolean =
        members(roomId).contains(memberId.toString())

    override fun appendChat(roomId: Long, chat: WaveChat) {
        val chats = chats(roomId)
        chats.add(chat)
        chats.trim(-CAPACITY, -1)
        chats.expire(TTL)
    }

    override fun recentChats(roomId: Long): List<WaveChat> = chats(roomId).readAll()

    override fun clear(roomId: Long) {
        chats(roomId).delete()
        members(roomId).delete()
    }

    private fun chats(roomId: Long): RList<WaveChat> = redisson.getList("wave:room:$roomId:chats")

    /**
     * 회원 id 를 문자열로, 공용 코덱이 아니라 [StringCodec] 으로 담는다.
     *
     * 공용 코덱(JsonJacksonCodec)은 final 타입에 타입 정보를 안 붙이고 디코딩은 Object 로 해서,
     * 작은 수가 Integer 로 되돌아온다. 그러면 `Set<Long>` 인 척하는 Integer 집합이 되어
     * `contains(1L)` 이 조용히 false 가 된다. (MemberOnlineTracker 와 같은 이유)
     *
     * 문자열로 담는 것만으로는 부족하다. 공용 코덱은 문자열도 JSON 으로 감싸(`5` → `"5"`) 저장하는데,
     * [joinIfNotFullScript] 는 레디스가 받은 인자를 그대로 SADD 하므로 두 경로의 바이트가 어긋난다.
     * 그러면 스크립트로 넣은 사람이 [isParticipant] 에서 안 보인다. 양쪽 다 [StringCodec] 으로 묶어
     * 인코딩을 하나로 만든다.
     */
    private fun members(roomId: Long): RSet<String> = redisson.getSet(membersKey(roomId), StringCodec.INSTANCE)

    private fun membersKey(roomId: Long) = "wave:room:$roomId:participants"

    companion object {
        /** 늦게 들어온 사람이 흐름을 따라잡을 만큼만. 전체 기록을 보관하는 자리가 아니다. */
        const val CAPACITY = 200

        // 방이 하루 넘게 열려 있진 않다. 정상 종료되면 clear 가 먼저 지우고, TTL 은 그 실패에 대한 보험이다.
        private val TTL: Duration = Duration.ofHours(6)
    }
}
