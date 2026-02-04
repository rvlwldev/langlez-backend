package com.langlez.auth.application

class OAuthAttributes(
    val attributes: Map<String, Any>,
    val nameAttributeKey: String,
    val name: String,
    val email: String,
    val picture: String?,
    val provider: String,
) {
    companion object {
        fun of(
            registrationId: String,
            userNameAttributeName: String,
            attributes: Map<String, Any>,
        ): OAuthAttributes =
            when (registrationId) {
                "google" -> ofGoogle(userNameAttributeName, attributes)
                "apple" -> ofApple(userNameAttributeName, attributes)
                else -> throw IllegalArgumentException("Unsupported Provider: $registrationId")
            }

        private fun ofGoogle(
            userNameAttributeName: String,
            attributes: Map<String, Any>,
        ): OAuthAttributes =
            OAuthAttributes(
                name = attributes["name"] as String,
                email = attributes["email"] as String,
                picture = attributes["picture"] as String?,
                attributes = attributes,
                nameAttributeKey = userNameAttributeName,
                provider = "google",
            )

        private fun ofApple(
            userNameAttributeName: String,
            attributes: Map<String, Any>,
        ): OAuthAttributes =
            OAuthAttributes(
                // Apple login often doesn't return name in ID token on subsequent logins
                name = (attributes["name"] as? String) ?: "Apple User",
                email = attributes["email"] as String,
                picture = null, // Apple doesn't provide profile picture
                attributes = attributes,
                nameAttributeKey = userNameAttributeName,
                provider = "apple",
            )
    }
}
