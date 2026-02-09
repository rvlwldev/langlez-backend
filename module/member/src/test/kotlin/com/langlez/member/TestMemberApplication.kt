package com.langlez.member

import com.langlez.member.config.TestSecurityConfig
import com.langlez.security.filter.JwtAuthenticationFilter
import com.langlez.security.token.JwtTokenProvider
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@Import(TestSecurityConfig::class, JwtTokenProvider::class, JwtAuthenticationFilter::class)
@ComponentScan(basePackages = ["com.langlez.member", "com.langlez.common.exception"])
@EntityScan("com.langlez.member.domain")
@EnableJpaRepositories("com.langlez.member")
class TestMemberApplication
