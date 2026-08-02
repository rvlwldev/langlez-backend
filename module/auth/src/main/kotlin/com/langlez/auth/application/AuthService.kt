package com.langlez.auth.application

import com.langlez.auth.domain.OAuth2UserProfile
import com.langlez.auth.oauth2.OAuth2LanglezUser
import com.langlez.core.TokenBlacklist
import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberService
import com.langlez.member.domain.MemberProvider
import com.langlez.utility.JwtTokenProvider
import org.redisson.api.RedissonClient
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
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val user = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val nameAttributeKey = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        val profile = OAuth2UserProfile.by(registrationId, nameAttributeKey, user.attributes)

        return oauth2Login(profile)
    }

    private fun oauth2Login(profile: OAuth2UserProfile): OAuth2User {
        val type = MemberProvider.valueOf(profile.provider.uppercase())
        val id = profile.rawAttributes[profile.providerKey]?.toString()
            ?: throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request")
        val member = service.findByProvider(type, id) ?: run {
            val email = profile.email ?: throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request")
            if (service.findByEmail(email) != null) throw LanglezException(HttpStatus.CONFLICT, "auth.email-conflict")
            val name = (profile.displayName).take(20)

            return@run service.createMember(type, id, email, name, name)
        }

        return OAuth2LanglezUser(
            member.id,
            member.username,
            "ROLE_${member.role.name.uppercase()}",
            profile.rawAttributes,
            profile.providerKey
        )
    }

    /**
     * 토큰 쌍을 발급하고 refresh token을 Redis에 기록한다. OAuth2 로그인 성공 시와
     * 토큰 갱신 시 모두 이 메서드를 거쳐야 refresh token 회전 검증(refresh())이 일관되게 동작한다.
     */
    fun issueTokens(id: Long, username: String, role: String): Pair<String, String> {
        val refreshToken = jwt.createRefreshToken(id, username, role)
        val accessToken = jwt.createAccessToken(id, username, role)

        redisson.getBucket<String>(refreshTokenKey(id)).set(refreshToken, REFRESH_TOKEN_TTL)

        return refreshToken to accessToken
    }

    fun refresh(refreshToken: String): Pair<String, String> {
        val tokenType = jwt.extractTokenType(refreshToken)
        if (tokenType != "refresh") throw LanglezException(401, "auth.invalid-token")

        val id = jwt.extractId(refreshToken)
        val member = service.findById(id) ?: throw LanglezException(401, "auth.invalid-token")

        val bucket = redisson.getBucket<String>(refreshTokenKey(id))
        if (refreshToken != bucket.get()) {
            bucket.delete()
            throw LanglezException(401, "auth.token-expired")
        }

        return issueTokens(id, member.username, member.role.name)
    }

    fun logout(memberId: Long, accessToken: String) {
        redisson.getBucket<String>(refreshTokenKey(memberId)).delete()
        tokenBlacklist.blacklist(accessToken, jwt.extractRemainingValiditySeconds(accessToken))
    }

    companion object {
        private val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(14)
        private fun refreshTokenKey(id: Long) = "refresh_token:$id"
    }
}
