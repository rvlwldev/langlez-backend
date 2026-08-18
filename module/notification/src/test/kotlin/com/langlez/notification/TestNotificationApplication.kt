package com.langlez.notification

import com.langlez.core.MessageBroadcaster
import com.langlez.core.OnlineTracker
import com.langlez.core.PushTokenQuery
import com.langlez.core.TokenBlacklist
import io.mockk.mockk
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

/**
 * notification 단독 컨텍스트.
 *
 * 온라인 판정(member)·실시간 전달(infra:redis)·토큰 블랙리스트(infra:redis)의 구현체는
 * 이 모듈 의존에 없다. 저장소 테스트가 목적이라 전부 대역으로 채운다.
 */
@SpringBootApplication(scanBasePackages = ["com.langlez"])
@EnableJpaRepositories(basePackages = ["com.langlez"])
@EntityScan(basePackages = ["com.langlez"])
class TestNotificationApplication {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        Mockito.mock(ClientRegistrationRepository::class.java)

    @Bean
    fun tokenBlacklist(): TokenBlacklist = mockk(relaxed = true)

    @Bean
    fun onlineTracker(): OnlineTracker = mockk(relaxed = true)

    @Bean
    fun messageBroadcaster(): MessageBroadcaster = mockk(relaxed = true)

    @Bean
    fun pushTokenQuery(): PushTokenQuery = mockk(relaxed = true)
}
