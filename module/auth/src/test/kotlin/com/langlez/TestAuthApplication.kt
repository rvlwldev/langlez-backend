package com.langlez

import com.langlez.auth.config.TestSecurityConfig
import com.langlez.common.exception.GlobalRestControllerAdvice
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
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.langlez\\.MainApplication", "com\\.langlez\\.security\\.config\\.SecurityConfig"]
        )
    ]
)
@Import(TestSecurityConfig::class, JwtTokenProvider::class, GlobalRestControllerAdvice::class)
@EntityScan("com.langlez")
@EnableJpaRepositories("com.langlez")
class TestAuthApplication