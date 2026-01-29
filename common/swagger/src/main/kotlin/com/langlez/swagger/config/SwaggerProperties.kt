package com.langlez.swagger.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "swagger")
data class SwaggerProperties(
    val enabled: Boolean = false,
    val title: String = "Langlez API Documentation",
    val description: String = "Langlez Backend API Documentation",
    val version: String = "1.0.0",
    val contact: Contact = Contact(),
    val servers: List<Server> = emptyList(),
    val jwt: Jwt = Jwt(),
) {
    data class Contact(
        val name: String = "Langlez Team",
        val email: String = "dev@langlez.com",
        val url: String = "https://langlez.com",
    )

    data class Server(
        val url: String,
        val description: String,
    )

    data class Jwt(
        val enabled: Boolean = true,
        val headerName: String = "Authorization",
        val tokenPrefix: String = "Bearer ",
        val secret: String? = null,
    )
}
