package com.langlez.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebConfiguration {

    @Bean
    fun openAPI(): OpenAPI {
        val security = SecurityScheme()
            .`in`(SecurityScheme.In.HEADER)
            .type(SecurityScheme.Type.HTTP)
            .name("Authorization")
            .scheme("bearer")
            .bearerFormat("JWT")

        return OpenAPI()
            .components(Components().addSecuritySchemes("bearer-jwt", security))
            .security(listOf(SecurityRequirement().addList("bearer-jwt")))
    }

}