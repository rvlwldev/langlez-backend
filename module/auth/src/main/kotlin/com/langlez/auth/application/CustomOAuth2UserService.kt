package com.langlez.auth.application

import com.langlez.member.application.MemberService
import com.langlez.member.application.command.CreateMemberCommand
import com.langlez.member.domain.Member
import com.langlez.member.domain.embedded.MemberProvider
import java.util.*
import org.springframework.context.annotation.Lazy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Lazy
@Service
class CustomOAuth2UserService(
    private val service: MemberService,
    private val delegate: OAuth2UserService<OAuth2UserRequest, OAuth2User> = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val user = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val userNameAttributeName = userRequest
            .clientRegistration
            .providerDetails
            .userInfoEndpoint
            .userNameAttributeName
        val attributes = OAuthAttributes.of(registrationId, userNameAttributeName, user.attributes)
        val member = saveOrUpdate(attributes)

        return DefaultOAuth2User(
            Collections.singleton(SimpleGrantedAuthority("ROLE_${member.role.name}")),
            attributes.attributes,
            attributes.nameAttributeKey,
        )
    }

    private fun saveOrUpdate(attributes: OAuthAttributes): Member {
        val providerType = when (attributes.provider.uppercase()) {
            "APPLE" -> MemberProvider.Type.APPLE
            "GOOGLE" -> MemberProvider.Type.GOOGLE
            else -> throw IllegalArgumentException("Unsupported Provider: ${attributes.provider}")
        }

        val providerId = attributes.attributes[attributes.nameAttributeKey]?.toString()
            ?: throw IllegalArgumentException("Provider ID not found in attributes")

        return service.findOrCreateMember(
            CreateMemberCommand(
                email = attributes.email,
                nickname = attributes.name,
                agreeTerm = false,
                providerId = providerId,
                providerType = providerType,
                providerUserName = attributes.name
            )
        )
    }
}
