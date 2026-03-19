package com.langlez.auth.application

import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberCommand
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import com.langlez.security.util.JwtParser
import com.langlez.auth.domain.OAuth2UserProfile
import com.langlez.auth.oauth2.OAuth2LanglezUser
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
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
    private val repo: MemberRepository,
    private val redis: StringRedisTemplate,
    private val memberService: MemberService,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val user = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val nameAttributeKey = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        val profile = OAuth2UserProfile.by(registrationId, nameAttributeKey, user.attributes)

        val member: Member = runCatching { loginMember(profile.email) }.getOrElse { e ->
            if (e !is LanglezException || e.status != NOT_FOUND) throw e

            val providerId = profile.rawAttributes[profile.providerKey]?.toString()
                ?: throw LanglezException(BAD_REQUEST, "auth.invalid-request")
            val providerType = MemberProvider.Type.valueOf(profile.provider.uppercase())
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

    @Transactional
    fun loginMember(email: String): Member {
        val member = repo.findByEmail(email)
            ?: throw LanglezException(NOT_FOUND, "member.not-found")

        member.login()

        return repo.save(member)
    }

    fun refresh(refreshToken: String): Pair<String, String> {
        val id = jwt.extractID(refreshToken)

        val member = repo.findById(id)
            ?: throw LanglezException(UNAUTHORIZED, "auth.invalid-token")

        if (refreshToken != redis.opsForValue().get("refresh_token:$id"))
            throw LanglezException(UNAUTHORIZED, "auth.token-expired")

        return Pair(
            jwt.createRefreshToken(member.id, member.role.name),
            jwt.createAccessToken(member.id, member.role.name)
        ).also { redis.opsForValue().set("refresh_token:$id", it.first, 14, TimeUnit.DAYS) }
    }
}
