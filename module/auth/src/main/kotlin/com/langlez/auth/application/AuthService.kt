package com.langlez.auth.application

import com.langlez.auth.domain.OAuth2UserProfile
import com.langlez.auth.oauth2.OAuth2LanglezUser
import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.security.TokenManager
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class AuthService(
    private val tokens: TokenManager,
    private val service: MemberService,
    private val redisson: RedissonClient,
    private val onlineTracker: MemberOnlineTracker,
    @param:Value($$"${jwt.access-token-ttl-secs}") private val accessTokenTtlSecs: Long,
    @param:Value($$"${jwt.refresh-token-ttl-secs}") private val refreshTokenTtlSecs: Long,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    val accessTokenTtl: Duration get() = Duration.ofSeconds(accessTokenTtlSecs)
    val refreshTokenTtl: Duration get() = Duration.ofSeconds(refreshTokenTtlSecs)

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val user = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val nameAttributeKey = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        val profile = OAuth2UserProfile.by(registrationId, nameAttributeKey, user.attributes)

        return oauth2Login(profile)
    }

    private fun oauth2Login(profile: OAuth2UserProfile): OAuth2User {
        val type = Member.Provider.valueOf(profile.provider.uppercase())
        val id = profile.rawAttributes[profile.providerKey]?.toString()
            ?: throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request")
        val member = service.findByProvider(type, id) ?: run {
            val email = profile.email ?: throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request")
            if (service.findByEmail(email) != null) throw LanglezException(HttpStatus.CONFLICT, "auth.email-conflict")
            val name = (profile.displayName).take(20)

            return@run service.createMember(type, id, email, name)
        }

        // 정지/탈퇴 회원이 소셜 로그인으로 되살아나면 안 된다.
        try {
            member.requireActive()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.FORBIDDEN, e.message, e)
        }

        return OAuth2LanglezUser(
            member.id,
            member.handle,
            member.role.authority,
            profile.rawAttributes,
            profile.providerKey
        )
    }

    /**
     * 1인 1기기. 세션은 회원당 하나뿐이라 새 기기에서 로그인하면 이전 기기의
     * 리프레시 토큰과 기기 바인딩이 함께 덮어써져 이전 세션이 끊긴다.
     * (이전 기기의 access token 은 남은 TTL 동안만 유효하다.)
     */
    fun issueTokens(id: Long, handle: String, role: String, ctx: AccessContext): Pair<String, String> {
        val refreshToken = tokens.issueRefreshToken(id, handle, role)
        val accessToken = tokens.issueAccessToken(id, handle, role)

        refreshTokenBucket(id).set(refreshToken, refreshTokenTtl)
        bindDevice(id, ctx.deviceId)

        onlineTracker.recordAccess(id, ctx.ip, ctx.deviceId)

        return refreshToken to accessToken
    }

    fun refresh(refreshToken: String, ctx: AccessContext): Pair<String, String> {
        val info = tokens.parse(refreshToken)
        if (info.type != TokenManager.Type.REFRESH) throw LanglezException(401, "auth.invalid-token")

        val id = info.memberId
        val member = service.findById(id) ?: throw LanglezException(401, "auth.invalid-token")

        try {
            member.requireActive()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.FORBIDDEN, e.message, e)
        }

        // 1인 1기기: 세션에 묶인 기기와 다르면 다른 기기에서 로그인해 밀려난 것이다.
        //
        // ctx.deviceId 가 null 인 경우를 통과시키면 안 된다. 헤더를 빼기만 하면 검증이
        // 건너뛰어져(fail-open), 탈취한 리프레시 토큰을 아무 기기에서나 쓸 수 있다.
        // 바인딩이 존재하면 반드시 일치해야 하고, 없으면 이번 기기로 바인딩한다(TOFU).
        //
        // 토큰 비교보다 앞에 둔다. 밀려난 기기의 토큰은 이미 회전으로 무효라 순서를 뒤집으면
        // 원인이 token-expired 로 뭉개진다. 회전을 시도조차 안 하는 편이 부수효과도 적다.
        val boundDevice = deviceBucket(id).get()
        if (boundDevice != null && boundDevice != ctx.deviceId) {
            throw LanglezException(401, "auth.session-taken-over")
        }

        val newRefreshToken = tokens.issueRefreshToken(id, member.handle, member.role.authority)
        val newAccessToken = tokens.issueAccessToken(id, member.handle, member.role.authority)

        // 회전은 원자 교체로 한다. 읽고-쓰기로 하면 동시 요청 둘이 같은 옛 토큰을 읽고 둘 다
        // 통과해, 나중에 쓴 쪽이 먼저 쓴 쪽의 토큰을 덮는다 — 진 쪽 클라이언트는 Redis 에 없는
        // 토큰을 들고 나간다.
        //
        // 불일치를 세션 삭제로 처리하지 않는다. 회전 때문에 "저장값과 다르다" 는 탈취뿐 아니라
        // 다른 요청이 방금 갱신했다는 뜻이기도 하다. 지워버리면 앱이 포그라운드 복귀 시 같은
        // 토큰으로 두 번 갱신하는 것만으로 재로그인을 강요당하고, 탈취한 옛 토큰을 던지는 것만으로
        // 피해자 세션을 끊을 수 있다. 거부는 하되 세션은 건드리지 않는다.
        //
        // 재사용 감지(RTR) — 무효 토큰이 다시 오면 전 세션을 파기할지 — 는 정책 미정이라
        // 여기서 정하지 않는다. README 5.3 참고.
        if (!rotate(id, from = refreshToken, to = newRefreshToken)) {
            throw LanglezException(401, "auth.token-expired")
        }

        bindDevice(id, ctx.deviceId)
        onlineTracker.recordAccess(id, ctx.ip, ctx.deviceId)

        return newRefreshToken to newAccessToken
    }

    /**
     * 기기 id 를 못 받은 발급은 이전 바인딩을 지운다.
     *
     * 남겨두면 새 기기의 첫 갱신이 옛 바인딩과 어긋나 401 로 잘리고, 재로그인해도 바인딩이
     * 그대로라 액세스 토큰 TTL 마다 반복된다. 이 시점엔 방금의 발급이 리프레시 토큰을 이미
     * 덮어써 옛 기기 세션이 끝난 뒤라, 남은 바인딩은 아무 세션도 지키지 않는 값이다.
     * 다음 갱신이 TOFU 로 그 기기를 다시 묶는다.
     */
    private fun bindDevice(id: Long, deviceId: String?) {
        val bucket = deviceBucket(id)
        deviceId?.let { bucket.set(it, refreshTokenTtl) } ?: bucket.delete()
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
    private fun rotate(id: Long, from: String, to: String): Boolean {
        val rotated: Long = redisson.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,
            ROTATE_SCRIPT,
            RScript.ReturnType.INTEGER,
            listOf(refreshTokenKey(id)),
            from,
            to,
            refreshTokenTtlSecs.toString(),
        )

        return rotated == 1L
    }

    private fun refreshTokenBucket(id: Long) =
        redisson.getBucket<String>(refreshTokenKey(id), StringCodec.INSTANCE)

    private fun deviceBucket(id: Long) =
        redisson.getBucket<String>(deviceKey(id), StringCodec.INSTANCE)

    fun logout(memberId: Long, accessToken: String) {
        invalidateSession(memberId)
        tokens.revoke(accessToken)
    }

    /**
     * 리프레시 토큰과 기기 바인딩만 지운다. 탈퇴 이벤트 소비 등, 지울 액세스 토큰 문자열을
     * 알 수 없는 경로에서 쓴다.
     *
     * 잔여 액세스 토큰은 여기서 블랙리스트에 넣지 않는다. `JwtAuthenticationFilter` 가 매 요청
     * `MemberReader` 로 회원 상태를 확인해 WITHDRAWN 이면 이미 막는다(PR #3) — 이 필터는
     * 탈퇴 시점 이후 발급된 토큰이 없으므로 예외 없이 전부 걸린다. 개별 토큰을 블랙리스트에
     * 추가하려면 토큰 문자열이나 jti 가 필요한데 탈퇴 이벤트에는 없고, 이를 위해 리프레시 토큰
     * 저장소를 뒤지거나 별도 저장을 새로 만드는 비용이 이미 막혀 있는 구멍을 다시 막는 값을
     * 넘는다.
     */
    fun invalidateSession(memberId: Long) {
        refreshTokenBucket(memberId).delete()
        deviceBucket(memberId).delete()
    }

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
