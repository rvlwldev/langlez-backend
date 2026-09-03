package com.langlez.rdb

import com.langlez.member.contract.MemberReader
import io.mockk.mockk
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.langlez"])
@EnableJpaRepositories(basePackages = ["com.langlez"])
@EntityScan(basePackages = ["com.langlez"])
class TestRdbApplication {

    /** TokenManager 의 토큰 차단 저장이 Redisson 직결이다. infra:rdb 단독 컨텍스트엔 없어 대역을 쓴다. */
    @Bean
    fun redissonClient(): RedissonClient = mockk(relaxed = true)

    /** 구현체는 module:member 에 있다. JwtAuthenticationFilter 가 요구하지만 이 컨텍스트엔 회원이 없다. */
    @Bean
    fun memberReader(): MemberReader = object : MemberReader {
        override fun findIdByHandle(handle: String): Long? = null
        override fun findProfileInfo(memberId: Long): MemberReader.ProfileInfo? = null
        override fun findProfileInfos(memberIds: Collection<Long>) = emptyMap<Long, MemberReader.ProfileInfo>()
        override fun findStatus(memberId: Long) = MemberReader.Status.ACTIVE
    }
}
