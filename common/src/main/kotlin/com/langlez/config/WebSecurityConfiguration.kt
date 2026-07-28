package com.langlez.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.annotation.MemberIdResolver
import com.langlez.annotation.MemberRoleResolver
import com.langlez.exception.ExceptionResponse
import com.langlez.filter.JwtAuthenticationFilter
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN
import jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebSecurity
class WebSecurityConfiguration(
    private val mapper: ObjectMapper,
    private val idResolver: MemberIdResolver,
    private val roleResolver: MemberRoleResolver,
    private val oauth2Service: OAuth2UserService<OAuth2UserRequest, OAuth2User>?,
    private val oauth2SuccessHandler: AuthenticationSuccessHandler?,
) : WebMvcConfigurer {

    @Bean
    fun filterChain(http: HttpSecurity, jwtFilter: JwtAuthenticationFilter): SecurityFilterChain {
        http.cors { it.disable() } // CORS 비활성화, 모바일앱 전용
            .csrf { it.disable() } // CSRF 비활성화
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) } // JWT
            .authorizeHttpRequests { customizer ->
                val actuator = arrayOf("/actuator/health", "/actuator/info", "/actuator/prometheus")
                val swagger = arrayOf("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**")
                val oauth2 = arrayOf("/oauth2/**", "/login/oauth2/**")
                val auth = arrayOf("/api/v1/auth/**")

                customizer
                    .requestMatchers(*oauth2, *swagger, *auth, *actuator).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, AuthorizationFilter::class.java)
            .exceptionHandling { config ->
                config.authenticationEntryPoint { _, res, _ -> toJSON(res, SC_UNAUTHORIZED, "auth.unauthorized") }
                config.accessDeniedHandler { _, res, _ -> toJSON(res, SC_FORBIDDEN, "auth.forbidden") }
            }

        if (oauth2Service != null && oauth2SuccessHandler != null) {
            http.oauth2Login {
                it.userInfoEndpoint { config -> config.userService(oauth2Service) }
                it.successHandler(oauth2SuccessHandler)
            }
        }

        return http.build()
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(idResolver)
        resolvers.add(roleResolver)
    }

    private fun toJSON(res: HttpServletResponse, status: Int, message: String) {
        res.status = status
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.characterEncoding = "UTF-8"
        val response = ExceptionResponse(status, message)
        res.writer.write(mapper.writeValueAsString(response))
    }
}