package com.langlez.member

import com.langlez.core.Storage
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

@SpringBootApplication(scanBasePackages = ["com.langlez"])
@EnableJpaRepositories(basePackages = ["com.langlez"])
@EntityScan(basePackages = ["com.langlez"])
class TestMemberApplication {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        return Mockito.mock(ClientRegistrationRepository::class.java)
    }

    /** Storage 구현체는 attachment 모듈에 있다. member 단독 컨텍스트에선 대역을 쓴다. */
    @Bean
    fun storage(): Storage = object : Storage {
        override fun presign(id: Long, source: String, type: Storage.Type, filename: String) =
            Storage.PresignedResult("$source/$filename", "https://presigned.test/$filename")

        override fun attach(key: String, sourceId: Long?) = "https://cdn.test/$key"
    }

    /**
     * RedisMessageBroadcaster 가 요구한다. 이 빈은 @EnableWebSocketMessageBroker 가 등록하는데,
     * 그 설정은 chat 모듈에 있어 이 모듈 단독 컨텍스트엔 없다. 대역만 올린다.
     */
    @Bean
    fun simpMessagingTemplate(): org.springframework.messaging.simp.SimpMessagingTemplate =
        Mockito.mock(org.springframework.messaging.simp.SimpMessagingTemplate::class.java)
}
