package com.langlez.follow

import com.langlez.attachment.contract.Storage
import com.langlez.block.contract.BlockReader
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

@SpringBootApplication(scanBasePackages = ["com.langlez"])
@EnableJpaRepositories(basePackages = ["com.langlez"])
@EntityScan(basePackages = ["com.langlez"])
class TestFollowApplication {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        Mockito.mock(ClientRegistrationRepository::class.java)

    /** Storage 구현체는 attachment 모듈에 있다. follow 단독 컨텍스트에선 대역을 쓴다. */
    @Bean
    fun storage(): Storage = object : Storage {
        override fun presign(id: Long, source: String, type: Storage.Type, filename: String) =
            Storage.PresignedResult("$source/$filename", "https://presigned.test/$filename")

        override fun attach(key: String, sourceId: Long?) = "https://cdn.test/$key"
    }

    /**
     * BlockReader 구현체는 block 모듈에 있다. follow 단독 컨텍스트에선 차단이 없는 것으로 둔다.
     * 차단이 걸린 경로의 판정은 FollowServiceTest 가 목으로 본다.
     */
    @Bean
    fun blockReader(): BlockReader = object : BlockReader {
        override fun isBlockedBetween(memberId: Long, otherId: Long) = false

        override fun blockedAmong(viewerId: Long, candidateIds: Collection<Long>) = emptySet<Long>()
    }

    /** RedisMessageBroadcaster 가 요구한다. 등록 주체(@EnableWebSocketMessageBroker)는 chat 모듈에 있다. */
    @Bean
    fun simpMessagingTemplate(): SimpMessagingTemplate = Mockito.mock(SimpMessagingTemplate::class.java)
}
