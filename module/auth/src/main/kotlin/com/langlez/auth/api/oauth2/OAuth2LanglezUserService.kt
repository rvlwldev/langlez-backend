package com.langlez.auth.api.oauth2

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberAuthenticator
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Component

@Component
class OAuth2LanglezUserService(
    private val members: MemberAuthenticator,
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private val delegate = DefaultOAuth2UserService()

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val user = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val nameAttributeKey = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        val profile = OAuth2UserProfile.by(registrationId, nameAttributeKey, user.attributes)

        val providerId = profile.rawAttributes[profile.providerKey]?.toString()
            ?: throw LanglezException(HttpStatus.BAD_REQUEST, "auth.invalid-request")

        val account = members.authenticate(profile.provider, providerId, profile.email, profile.displayName)

        return OAuth2LanglezUser(account.id, account.handle, account.role, profile.rawAttributes, profile.providerKey)
    }
}
