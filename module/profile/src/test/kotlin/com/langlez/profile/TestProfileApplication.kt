package com.langlez.profile

import com.langlez.attachment.contract.Storage
import com.langlez.follow.contract.FollowReader
import com.langlez.lang.contract.LanguageReader
import com.langlez.member.contract.MemberReader
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

@SpringBootApplication(scanBasePackages = ["com.langlez"])
@EnableJpaRepositories(basePackages = ["com.langlez"])
@EntityScan(basePackages = ["com.langlez"])
class TestProfileApplication {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        return Mockito.mock(ClientRegistrationRepository::class.java)
    }

    /** attach 는 key 를 조회용 URL 로 바꿔주는 계약이다. null 을 돌려주면 확정 흐름이 통째로 깨진다. */
    @Bean
    fun storage(): Storage = object : Storage {
        override fun presign(
            id: Long,
            source: String,
            type: Storage.Type,
            filename: String,
        ) = Storage.PresignedResult("$source/$filename", "https://presigned.test/$filename")

        override fun attach(key: String, sourceId: Long?) = "https://cdn.test/$key"
    }

    /** 구현체는 follow 모듈에 있다. profile 단독 컨텍스트엔 없어서 대역을 쓴다. */
    @Bean
    fun followReader(): FollowReader = object : FollowReader {
        override fun followingIds(memberId: Long) = emptyList<Long>()

        override fun counts(memberId: Long) = FollowReader.CountInfo(0, 0)
    }

    /**
     * 구현체는 member 모듈에 있다. profile 단독 컨텍스트엔 없어서 대역을 쓴다.
     * 상태 조회는 JwtAuthenticationFilter 가 매 요청 부른다 — relaxed mock 은 enum 반환값을
     * 보장하지 않아 명시 대역을 쓴다.
     */
    @Bean
    fun memberReader(): MemberReader = object : MemberReader {
        override fun findIdByHandle(handle: String): Long? = null

        override fun findProfileInfo(memberId: Long): MemberReader.ProfileInfo? = null

        override fun findProfileInfos(memberIds: Collection<Long>) = emptyMap<Long, MemberReader.ProfileInfo>()

        override fun findStatus(memberId: Long) = MemberReader.Status.ACTIVE
    }

    /** 구현체는 lang 모듈에 있다. profile 단독 컨텍스트엔 없어서 언어가 없는 것으로 둔다. */
    @Bean
    fun languageReader(): LanguageReader = object : LanguageReader {
        override fun languagesOf(memberId: Long) = emptyList<LanguageReader.LanguageInfo>()

        override fun languagesOf(memberIds: Collection<Long>) =
            emptyMap<Long, List<LanguageReader.LanguageInfo>>()

        override fun complementaryCandidates(
            myNativeLanguages: Collection<String>,
            myLearningLanguages: Collection<String>,
            excludeMemberId: Long,
            limit: Int,
        ) = emptyList<Long>()
    }

    /**
     * RedisMessageBroadcaster 가 요구한다. 이 빈은 @EnableWebSocketMessageBroker 가 등록하는데,
     * 그 설정은 chat 모듈에 있어 이 모듈 단독 컨텍스트엔 없다. 대역만 올린다.
     */
    @Bean
    fun simpMessagingTemplate(): org.springframework.messaging.simp.SimpMessagingTemplate =
        Mockito.mock(org.springframework.messaging.simp.SimpMessagingTemplate::class.java)
}
