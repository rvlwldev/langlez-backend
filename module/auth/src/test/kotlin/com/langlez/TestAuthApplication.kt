package com.langlez

import com.langlez.auth.config.TestSecurityConfig
import com.langlez.security.config.SecurityConfig
import com.langlez.security.filter.JwtAuthenticationFilter
import com.langlez.security.token.JwtTokenProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@ComponentScan(
        basePackages = ["com.langlez"],
        excludeFilters =
                [
                        ComponentScan.Filter(
                                type = FilterType.ASSIGNABLE_TYPE,
                                classes = [SecurityConfig::class]
                        )]
)
@EntityScan("com.langlez")
@EnableJpaRepositories("com.langlez")
@Import(TestSecurityConfig::class, JwtTokenProvider::class, JwtAuthenticationFilter::class)
class TestAuthApplication
