package com.langlez.swagger.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(SwaggerProperties::class)
@ConditionalOnProperty(prefix = "swagger", name = ["enabled"], havingValue = "true")
open class SwaggerAutoConfiguration(
    private val swaggerProperties: SwaggerProperties,
) {
    @Bean
    open fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title(swaggerProperties.title)
                    .description(swaggerProperties.description)
                    .version(swaggerProperties.version)
                    .contact(
                        Contact()
                            .name(swaggerProperties.contact.name)
                            .email(swaggerProperties.contact.email)
                            .url(swaggerProperties.contact.url),
                    ).license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0"),
                    ),
            ).servers(
                swaggerProperties.servers.map { server ->
                    Server()
                        .url(server.url)
                        .description(server.description)
                },
            ).components(
                Components()
                    .addSecuritySchemes(
                        "bearer-jwt",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .`in`(SecurityScheme.In.HEADER)
                            .name(swaggerProperties.jwt.headerName),
                    ),
            ).security(listOf(SecurityRequirement().addList("bearer-jwt")))
}
