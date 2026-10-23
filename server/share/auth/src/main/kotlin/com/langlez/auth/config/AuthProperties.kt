package com.langlez.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("langlez.auth")
data class AuthProperties(
    val supabaseUrl: String,
    val audience: String = "authenticated",
    val publicPaths: List<String> = listOf("/actuator/health/**"),
) {
    fun issuer(): String = "${supabaseUrl.trimEnd('/')}/auth/v1"

    fun jwkSetUri(): String = "${issuer()}/.well-known/jwks.json"
}
