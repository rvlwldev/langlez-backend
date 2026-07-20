package com.langlez.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.exception.ExceptionResponse
import com.langlez.security.web.JwtAuthenticationFilter
import com.langlez.security.web.MemberIdArgumentResolver
import com.langlez.security.web.MemberRoleArgumentResolver
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebSecurity
class WebSecurityConfiguration(
    private val memberIdArgumentResolver: MemberIdArgumentResolver,
    private val memberRoleArgumentResolver: MemberRoleArgumentResolver,
    private val objectMapper: ObjectMapper,
    @param:Lazy private val oauth2UserService: OAuth2UserService<OAuth2UserRequest, OAuth2User>?,
    @param:Lazy private val oauth2SuccessHandler: AuthenticationSuccessHandler?,
    @param:Value($$"${app.cors.allowed-origins:http://localhost:3000}") private val allowedOrigins: List<String>,
) : WebMvcConfigurer {

    @Bean
    fun filterChain(http: HttpSecurity, filter: JwtAuthenticationFilter): SecurityFilterChain =
        http.cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { customizer ->
                val swagger = arrayOf("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**")
                val oauth2 = arrayOf("/oauth2/**", "/login/oauth2/**")
                val auth = arrayOf("/api/v1/auth/**")
                val actuator = arrayOf("/actuator/health", "/actuator/info", "/actuator/prometheus")

                customizer.requestMatchers(*oauth2, *swagger, *auth, *actuator).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { handler ->
                handler.authenticationEntryPoint { _, res, _ -> writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, "auth.unauthorized") }
                handler.accessDeniedHandler { _, res, _ -> writeJson(res, HttpServletResponse.SC_FORBIDDEN, "auth.forbidden") }
            }
            .oauth2Login { oauth2 ->
                oauth2UserService?.let { oauth2.userInfoEndpoint { it.userService(oauth2UserService) } }
                oauth2SuccessHandler?.let { oauth2.successHandler(it) }
            }
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@WebSecurityConfiguration.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(memberIdArgumentResolver)
        resolvers.add(memberRoleArgumentResolver)
    }

    private fun writeJson(res: HttpServletResponse, status: Int, message: String) {
        res.status = status
        res.contentType = MediaType.APPLICATION_JSON_VALUE
        res.characterEncoding = "UTF-8"
        val response = ExceptionResponse(status, message)
        res.writer.write(objectMapper.writeValueAsString(response))
    }
}