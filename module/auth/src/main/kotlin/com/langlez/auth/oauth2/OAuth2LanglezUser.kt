package com.langlez.auth.oauth2

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class OAuth2LanglezUser(
    val id: Long,
    val role: String,
    private val attributes: Map<String, Any>,
    private val nameAttributeKey: String
) : OAuth2User {
    override fun getAttributes(): Map<String, Any> = this.attributes
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority(role))
    override fun getName(): String? = attributes[nameAttributeKey]?.toString()
}