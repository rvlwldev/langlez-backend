package com.langlez.auth.config

import com.langlez.file.application.FileStorage
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.multipart.MultipartFile

@TestConfiguration
@EnableWebSecurity
class TestSecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }.authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Primary
    fun testFileStorage(): FileStorage {
        return object : FileStorage {
            override fun upload(file: MultipartFile, folder: String?): String =
                    "https://mock-s3.com/test.jpg"

            override fun delete(fileUrl: String) {}
        }
    }
}
