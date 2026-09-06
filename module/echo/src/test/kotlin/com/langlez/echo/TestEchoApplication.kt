package com.langlez.echo

import com.langlez.attachment.contract.Storage
import com.langlez.block.contract.BlockReader
import com.langlez.member.contract.MemberReader
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

/**
 * echo 단독 컨텍스트. 다른 모듈이 구현하는 계약만 대역으로 채운다.
 * `FollowReader` 는 EchoService 가 nullable 로 받으므로 대역이 필요 없다.
 */
@SpringBootApplication(scanBasePackages = ["com.langlez"])
@EnableJpaRepositories(basePackages = ["com.langlez"])
@EntityScan(basePackages = ["com.langlez"])
class TestEchoApplication {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        Mockito.mock(ClientRegistrationRepository::class.java)

    /** Storage 구현체는 attachment 모듈에 있다. */
    @Bean
    fun storage(): Storage = object : Storage {
        override fun presign(id: Long, source: String, type: Storage.Type, filename: String) =
            Storage.PresignedResult("$source/$filename", "https://presigned.test/$filename")

        override fun attach(key: String, sourceId: Long?) = "https://cdn.test/$key"
    }

    /** 차단 판정은 block 모듈이 구현한다. 여기선 아무도 차단하지 않은 것으로 둔다. */
    @Bean
    fun blockReader(): BlockReader = object : BlockReader {
        override fun isBlockedBetween(memberId: Long, otherId: Long) = false

        override fun blockedAmong(viewerId: Long, candidateIds: Collection<Long>) = emptySet<Long>()
    }

    /** common 의 JwtAuthenticationFilter 가 매 요청 계정 상태를 본다. member 모듈이 구현한다. */
    @Bean
    fun memberReader(): MemberReader = Mockito.mock(MemberReader::class.java)

    /** RedisMessageBroadcaster 가 요구한다. 등록 주체(@EnableWebSocketMessageBroker)는 chat 모듈에 있다. */
    @Bean
    fun simpMessagingTemplate(): SimpMessagingTemplate = Mockito.mock(SimpMessagingTemplate::class.java)
}
