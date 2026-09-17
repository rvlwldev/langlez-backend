package com.langlez.auth.infrastructure

import com.langlez.auth.application.SessionStore
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisSessionStore(
    private val redisson: RedissonClient,
    @param:Value($$"${jwt.refresh-token-ttl-secs}") private val refreshTokenTtlSecs: Long,
) : SessionStore {

    private val refreshTokenTtl: Duration get() = Duration.ofSeconds(refreshTokenTtlSecs)

    override fun open(memberId: Long, refreshToken: String, deviceId: String?) {
        refreshTokenBucket(memberId).set(refreshToken, refreshTokenTtl)
        bindDevice(memberId, deviceId)
    }

    /**
     * 저장된 토큰이 [from] 일 때만 [to] 로 바꾸고 TTL 을 다시 건다. 교체했으면 true.
     *
     * 비교·교체·만료를 한 스크립트로 묶는다. `RBucket.compareAndSet` + `expire` 로 나누면
     * 그 사이에 배포(SIGTERM)나 OOM 으로 프로세스가 죽었을 때 TTL 없는 영구 키가 남는다 —
     * compareAndSet 이 쓰는 SET 에는 만료가 없어 기존 TTL 이 날아가기 때문이다. 그러면
     * 리프레시 토큰 2주 만료 정책이 그 회원에게만 조용히 사라진다.
     *
     * 코덱을 [StringCodec] 으로 못 박는다. 기본 코덱은 값을 JSON 으로 감싸므로 Lua 가 보는
     * 바이트와 `RBucket` 이 쓰는 바이트가 달라져 비교가 영영 실패한다. 이 키를 읽고 쓰는
     * 경로는 전부 [refreshTokenBucket] 을 거쳐 같은 코덱을 쓴다.
     */
    override fun rotate(memberId: Long, from: String, to: String): Boolean {
        val rotated: Long = redisson.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,
            ROTATE_SCRIPT,
            RScript.ReturnType.INTEGER,
            listOf(refreshTokenKey(memberId)),
            from,
            to,
            refreshTokenTtlSecs.toString(),
        )

        return rotated == 1L
    }

    override fun boundDevice(memberId: Long): String? = deviceBucket(memberId).get()

    /**
     * 기기 id 를 못 받은 발급은 이전 바인딩을 지운다.
     *
     * 남겨두면 새 기기의 첫 갱신이 옛 바인딩과 어긋나 401 로 잘리고, 재로그인해도 바인딩이
     * 그대로라 액세스 토큰 TTL 마다 반복된다. 이 시점엔 방금의 발급이 리프레시 토큰을 이미
     * 덮어써 옛 기기 세션이 끝난 뒤라, 남은 바인딩은 아무 세션도 지키지 않는 값이다.
     * 다음 갱신이 TOFU 로 그 기기를 다시 묶는다.
     */
    override fun bindDevice(memberId: Long, deviceId: String?) {
        val bucket = deviceBucket(memberId)
        deviceId?.let { bucket.set(it, refreshTokenTtl) } ?: bucket.delete()
    }

    override fun close(memberId: Long) {
        refreshTokenBucket(memberId).delete()
        deviceBucket(memberId).delete()
    }

    private fun refreshTokenBucket(id: Long) =
        redisson.getBucket<String>(refreshTokenKey(id), StringCodec.INSTANCE)

    private fun deviceBucket(id: Long) =
        redisson.getBucket<String>(deviceKey(id), StringCodec.INSTANCE)

    companion object {
        private val ROTATE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
        """.trimIndent()

        private fun refreshTokenKey(id: Long) = "refresh_token:$id"
        private fun deviceKey(id: Long) = "refresh_device:$id"
    }
}
