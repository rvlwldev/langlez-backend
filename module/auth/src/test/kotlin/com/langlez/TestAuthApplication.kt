package com.langlez

import com.langlez.auth.config.TestSecurityConfig
import com.langlez.security.config.SecurityConfig
import com.langlez.security.filter.JwtAuthenticationFilter
import com.langlez.security.token.JwtTokenProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import

@SpringBootApplication
@ComponentScan(
        basePackages =
                [
                        "com.langlez.auth",
                        "com.langlez.member",
                        "com.langlez.security",
                        "com.langlez.common.exception",
                        "com.langlez.common.observability"],
        excludeFilters =
                [
                        ComponentScan.Filter(
                                type = FilterType.ASSIGNABLE_TYPE,
                                classes = [SecurityConfig::class]
                        )]
)
@Import(TestSecurityConfig::class, JwtTokenProvider::class, JwtAuthenticationFilter::class)
class TestAuthApplication
