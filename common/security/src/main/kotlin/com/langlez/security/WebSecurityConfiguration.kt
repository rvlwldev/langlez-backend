package com.langlez.security

import com.langlez.security.web.JwtAuthenticationFilter
import com.langlez.security.web.MemberIdArgumentResolver
import com.langlez.security.web.MemberRoleArgumentResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebSecurity
class WebSecurityConfiguration(
    private val resolver: HandlerExceptionResolver,
    private val memberIdArgumentResolver: MemberIdArgumentResolver,
    private val memberRoleArgumentResolver: MemberRoleArgumentResolver
) : WebMvcConfigurer {

    @Bean
    fun filterChain(http: HttpSecurity, filter: JwtAuthenticationFilter): SecurityFilterChain =
        http.csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { customizer ->
                val swagger = arrayOf("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/api-docs/**")
                val oauth2 = arrayOf("/oauth2/**", "/login/oauth2/**")

                customizer.requestMatchers(*oauth2, *swagger).permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { handler ->
                handler.authenticationEntryPoint { req, res, e -> resolver.resolveException(req, res, null, e) }
                handler.accessDeniedHandler { req, res, e -> resolver.resolveException(req, res, null, e) }
            }
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(memberIdArgumentResolver)
        resolvers.add(memberRoleArgumentResolver)
    }

}