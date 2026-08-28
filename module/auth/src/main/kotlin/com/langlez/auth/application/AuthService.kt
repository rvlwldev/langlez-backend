package com.langlez.auth.application

import com.langlez.auth.domain.OAuth2UserProfile
import com.langlez.auth.oauth2.OAuth2LanglezUser
import com.langlez.core.TokenBlacklist
import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.utility.JwtTokenProvider
import org.redisson.api.RedissonClient
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
    private val jwt: JwtTokenProvider,
    private val service: MemberService,
    private val redisson: RedissonClient,
    private val tokenBlacklist: TokenBlacklist,
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
    fun issueTokens(id: Long, handle: String, role: String, ctx: AccessContext = AccessContext()): Pair<String, String> {
        val refreshToken = jwt.createRefreshToken(id, handle, role)
        val accessToken = jwt.createAccessToken(id, handle, role)

        redisson.getBucket<String>(refreshTokenKey(id)).set(refreshToken, refreshTokenTtl)
        ctx.deviceId?.let { redisson.getBucket<String>(deviceKey(id)).set(it, refreshTokenTtl) }

        onlineTracker.recordAccess(id, ctx.ip, ctx.deviceId)

        return refreshToken to accessToken
    }

    fun refresh(refreshToken: String, ctx: AccessContext = AccessContext()): Pair<String, String> {
        val tokenType = jwt.extractTokenType(refreshToken)
        if (tokenType != "refresh") throw LanglezException(401, "auth.invalid-token")

        val id = jwt.extractId(refreshToken)
        val member = service.findById(id) ?: throw LanglezException(401, "auth.invalid-token")

        try {
            member.requireActive()
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.FORBIDDEN, e.message, e)
        }

        val bucket = redisson.getBucket<String>(refreshTokenKey(id))
        if (refreshToken != bucket.get()) {
            bucket.delete()
            throw LanglezException(401, "auth.token-expired")
        }

        // 1인 1기기: 세션에 묶인 기기와 다르면 다른 기기에서 로그인해 밀려난 것이다.
        //
        // ctx.deviceId 가 null 인 경우를 통과시키면 안 된다. 헤더를 빼기만 하면 검증이
        // 건너뛰어져(fail-open), 탈취한 리프레시 토큰을 아무 기기에서나 쓸 수 있다.
        // 바인딩이 존재하면 반드시 일치해야 하고, 없으면 이번 기기로 바인딩한다(TOFU).
        val boundDevice = redisson.getBucket<String>(deviceKey(id)).get()
        if (boundDevice != null && boundDevice != ctx.deviceId) {
            throw LanglezException(401, "auth.session-taken-over")
        }

        return issueTokens(id, member.handle, member.role.authority, ctx)
    }

    fun logout(memberId: Long, accessToken: String) {
        invalidateSession(memberId)
        tokenBlacklist.blacklist(accessToken, jwt.extractRemainingValiditySeconds(accessToken))
    }

    /**
     * 리프레시 토큰과 기기 바인딩만 지운다. 탈퇴 이벤트 소비 등, 지울 액세스 토큰 문자열을
     * 알 수 없는 경로에서 쓴다.
     *
     * 잔여 액세스 토큰은 여기서 블랙리스트에 넣지 않는다. `JwtAuthenticationFilter` 가 매 요청
     * `MemberStatusQuery` 로 회원 상태를 확인해 WITHDRAWN 이면 이미 막는다(PR #3) — 이 필터는
     * 탈퇴 시점 이후 발급된 토큰이 없으므로 예외 없이 전부 걸린다. 개별 토큰을 블랙리스트에
     * 추가하려면 토큰 문자열이나 jti 가 필요한데 탈퇴 이벤트에는 없고, 이를 위해 리프레시 토큰
     * 저장소를 뒤지거나 별도 저장을 새로 만드는 비용이 이미 막혀 있는 구멍을 다시 막는 값을
     * 넘는다.
     */
    fun invalidateSession(memberId: Long) {
        redisson.getBucket<String>(refreshTokenKey(memberId)).delete()
        redisson.getBucket<String>(deviceKey(memberId)).delete()
    }

    companion object {
        private fun refreshTokenKey(id: Long) = "refresh_token:$id"
        private fun deviceKey(id: Long) = "refresh_device:$id"
    }
}
