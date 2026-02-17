package com.langlez.security.config.swagger

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Swagger UI 및 API 문서를 위한 별도의 보안 필터 체인
 * 일반 API 보안 설정보다 우선순위를 높게 설정(@Order(1))하여 정적 리소스를 허용합니다.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(prefix = "swagger", name = ["enabled"], havingValue = "true")
class SwaggerSecurityConfiguration {

    @Bean
    @Order(1)
    fun swaggerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
            )
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest().denyAll()
            }
            .csrf { it.disable() }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() } // H2 콘솔 등을 사용할 경우 대비
            }

        return http.build()
    }
}
