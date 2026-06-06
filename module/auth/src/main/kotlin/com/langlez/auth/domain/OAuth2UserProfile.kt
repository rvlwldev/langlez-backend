package com.langlez.auth.domain

data class OAuth2UserProfile(
    val provider: String,
    val providerKey: String,
    val email: String,
    val displayName: String,
    val rawAttributes: Map<String, Any>
) {
    companion object {
        fun by(provider: String, key: String, attributes: Map<String, Any>): OAuth2UserProfile =
            when (provider.uppercase()) {
                "APPLE" -> byApple(key, attributes)
                "GOOGLE" -> byGoogle(key, attributes)
                else -> throw IllegalArgumentException("Unsupported Provider: $provider")
            }

        private fun byApple(key: String, attributes: Map<String, Any>) = OAuth2UserProfile(
            provider = "APPLE",
            providerKey = key,
            email = attributes["email"] as String,
            displayName = (attributes["name"] as? String) ?: "AppleUser",
            rawAttributes = attributes,
        )

        private fun byGoogle(key: String, attributes: Map<String, Any>) = OAuth2UserProfile(
            provider = "GOOGLE",
            providerKey = key,
            email = attributes["email"] as String,
            displayName = attributes["name"] as? String ?: "GoogleUser",
            rawAttributes = attributes,
        )
    }
}