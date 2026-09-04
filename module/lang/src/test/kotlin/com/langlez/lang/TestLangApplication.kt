package com.langlez.lang

import com.langlez.member.contract.MemberReader
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
class TestLangApplication {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        Mockito.mock(ClientRegistrationRepository::class.java)

    /**
     * MemberReader 구현체는 member 모듈에 있다. lang 단독 컨텍스트에선 대역을 쓴다.
     * lang 의 프로덕션 코드는 member 를 모른다 — `JwtAuthenticationFilter`(common) 가 요구할 뿐이다.
     */
    @Bean
    fun memberReader(): MemberReader = object : MemberReader {
        override fun findIdByHandle(handle: String): Long? = null
        override fun findProfileInfo(memberId: Long): MemberReader.ProfileInfo? = null
        override fun findProfileInfos(memberIds: Collection<Long>) = emptyMap<Long, MemberReader.ProfileInfo>()
        override fun findStatus(memberId: Long): MemberReader.Status? = null
    }

    /** RedisMessageBroadcaster 가 요구한다. 등록 주체(@EnableWebSocketMessageBroker)는 chat 모듈에 있다. */
    @Bean
    fun simpMessagingTemplate(): SimpMessagingTemplate = Mockito.mock(SimpMessagingTemplate::class.java)
}
