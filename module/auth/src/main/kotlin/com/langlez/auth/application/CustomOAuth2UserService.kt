package com.langlez.auth.application

import com.langlez.member.application.CreateMemberCommand
import com.langlez.member.application.MemberUseCase
import com.langlez.member.domain.Member
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import java.util.Collections

@Service
class CustomOAuth2UserService(
    private val memberUseCase: MemberUseCase,
    private val delegate: OAuth2UserService<OAuth2UserRequest, OAuth2User> = DefaultOAuth2UserService(),
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)

        val registrationId = userRequest.clientRegistration.registrationId
        val userNameAttributeName = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName

        val attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.attributes)

        val member = saveOrUpdate(attributes)

        return DefaultOAuth2User(
            Collections.singleton(SimpleGrantedAuthority("ROLE_${member.role.name}")),
            attributes.attributes,
            attributes.nameAttributeKey,
        )
    }

    private fun saveOrUpdate(attributes: OAuthAttributes): Member =
        memberUseCase.findOrCreateMember(
            CreateMemberCommand(
                email = attributes.email,
                nickname = attributes.name,
                profileImageUrl = attributes.picture,
                provider = attributes.provider,
                providerId = attributes.nameAttributeKey,
            ),
        )
}
