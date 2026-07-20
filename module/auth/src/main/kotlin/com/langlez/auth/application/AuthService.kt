package com.langlez.auth.application

import com.langlez.core.LanglezException
import com.langlez.core.TokenBlacklist
import com.langlez.member.application.MemberCommand
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.security.util.JwtParser
import com.langlez.auth.domain.OAuth2UserProfile
import com.langlez.auth.oauth2.OAuth2LanglezUser
import org.redisson.api.RedissonClient
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
class AuthService(
    private val jwt: JwtParser,
    private val redisson: RedissonClient,
    private val memberService: MemberService,
    private val tokenBlacklist: TokenBlacklist,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val user = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val nameAttributeKey = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        val profile = OAuth2UserProfile.by(registrationId, nameAttributeKey, user.attributes)
        return processLogin(profile)
    }

    @Transactional
    fun processLogin(profile: OAuth2UserProfile): OAuth2User {
        val providerId = profile.rawAttributes[profile.providerKey]?.toString()
            ?: throw LanglezException(400, "auth.invalid-request")
        val providerType = Member.Provider.valueOf(profile.provider.uppercase())

        val member = memberService.findByProvider(providerId, providerType)
            ?.also { memberService.updateLastAccess(it.id) }
            ?: run {
                memberService.findByEmail(profile.email)?.let {
                    throw LanglezException(409, "auth.email-conflict")
                }
                val providerCmd = MemberCommand.Provider(providerId, providerType, profile.displayName)
                val createCmd = MemberCommand.Create(profile.email, null, profile.displayName)
                memberService.createMember(providerCmd, createCmd)
            }

        return OAuth2LanglezUser(
            member.id,
            "ROLE_${member.role.name.uppercase()}",
            profile.rawAttributes,
            profile.providerKey
        )
    }

    fun refresh(refreshToken: String): Pair<String, String> {
        val tokenType = jwt.extractTokenType(refreshToken)
        if (tokenType != "refresh")
            throw LanglezException(401, "auth.invalid-token")

        val id = jwt.extractId(refreshToken)
        val member = memberService.findById(id)
            ?: throw LanglezException(401, "auth.invalid-token")

        val bucket = redisson.getBucket<String>("refresh_token:$id")
        if (refreshToken != bucket.get()) {
            bucket.delete()
            throw LanglezException(401, "auth.token-expired")
        }

        return Pair(
            jwt.createRefreshToken(member.id, member.role.name),
            jwt.createAccessToken(member.id, member.role.name)
        ).also { bucket.set(it.first, 14, TimeUnit.DAYS) }
    }

    fun logout(memberId: Long, accessToken: String) {
        redisson.getBucket<String>("refresh_token:$memberId").delete()
        tokenBlacklist.blacklist(accessToken, jwt.extractRemainingValiditySeconds(accessToken))
    }
}
