package com.langlez.auth.domain

data class OAuth2UserProfile(
    val provider: String,
    val providerKey: String,
    val email: String?,
    val displayName: String,
    val rawAttributes: Map<String, Any>
) {
    companion object {
        fun by(provider: String, key: String, attributes: Map<String, Any>): OAuth2UserProfile =
            when (val upperProvider = provider.uppercase()) {
                "APPLE" -> createProfile(upperProvider, "AppleUser", key, attributes)
                "GOOGLE" -> createProfile(upperProvider, "GoogleUser", key, attributes)
                else -> throw IllegalArgumentException("Unsupported Provider: $provider")
            }

        private fun createProfile(
            provider: String,
            defaultDisplayName: String,
            key: String,
            attributes: Map<String, Any>
        ) = OAuth2UserProfile(
            provider = provider,
            providerKey = key,
            email = attributes["email"]?.toString()?.takeIf { it.isNotBlank() },
            displayName = (attributes["name"] as? String)?.takeIf { it.isNotBlank() } ?: defaultDisplayName,
            rawAttributes = attributes,
        )
    }
}