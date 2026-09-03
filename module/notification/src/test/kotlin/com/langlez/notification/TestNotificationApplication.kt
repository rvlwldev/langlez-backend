package com.langlez.notification

import com.langlez.core.MessageBroadcaster
import com.langlez.core.MessageDeduplicator
import com.langlez.member.contract.MemberReader
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.contract.PushTokenReader
import io.mockk.mockk
import org.mockito.Mockito
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

/**
 * notification 단독 컨텍스트.
 *
 * 온라인 판정·계정 상태 조회(member)·실시간 전달(infra:redis)·토큰 차단 저장(Redisson)의 구현체는
 * 이 모듈 의존에 없다. 저장소 테스트가 목적이라 전부 대역으로 채운다.
 */
@SpringBootApplication(scanBasePackages = ["com.langlez"])
@EnableJpaRepositories(basePackages = ["com.langlez"])
@EntityScan(basePackages = ["com.langlez"])
class TestNotificationApplication {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        Mockito.mock(ClientRegistrationRepository::class.java)

    /** TokenManager 의 토큰 차단 저장이 Redisson 직결이다. 이 컨텍스트엔 레디스가 없다. */
    @Bean
    fun redissonClient(): RedissonClient = mockk(relaxed = true)

    /**
     * 구현체는 infra:redis 에 있다. relaxed 대역은 `isDuplicate` 로 false 를 돌려줘
     * "중복 아님 = 통과" 로 흐른다 — 대역이 핸들러를 조용히 건너뛰지 않는 쪽이 기본값이어야 한다.
     */
    @Bean
    fun messageDeduplicator(): MessageDeduplicator = mockk(relaxed = true)

    @Bean
    fun onlineTracker(): OnlineTracker = mockk(relaxed = true)

    @Bean
    fun messageBroadcaster(): MessageBroadcaster = mockk(relaxed = true)

    @Bean
    fun pushTokenReader(): PushTokenReader = mockk(relaxed = true)

    /**
     * JwtAuthenticationFilter 가 요구한다. 구현체는 member 모듈에 있다.
     * relaxed mock 은 enum 반환값이 뭐가 될지 보장하지 않아 명시 대역을 쓴다.
     */
    @Bean
    fun memberReader(): MemberReader = object : MemberReader {
        override fun findIdByHandle(handle: String): Long? = null
        override fun findProfileInfo(memberId: Long): MemberReader.ProfileInfo? = null
        override fun findProfileInfos(memberIds: Collection<Long>) = emptyMap<Long, MemberReader.ProfileInfo>()
        override fun findStatus(memberId: Long) = MemberReader.Status.ACTIVE
    }
}
