package com.langlez.member

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * 토픽 정의. `KafkaAdmin` 이 기동 시 브로커에 없으면 만들고, 파티션이 모자라면 늘린다.
 *
 * 파티션은 (1) 같은 key 는 항상 같은 파티션으로 가므로 회원별 이벤트 순서가 보장되고,
 * (2) 컨슈머 그룹 안에서 파티션 단위로 병렬 처리된다.
 */
@Configuration
class MemberEventConfiguration {

    @Bean
    fun memberUsernameChangedTopic(): NewTopic = TopicBuilder
        .name("member-username-changed")
        .partitions(3)
        .replicas(3)
        .build()

    @Bean
    fun memberNicknameChangedTopic(): NewTopic = TopicBuilder
        .name("member-nickname-changed")
        .partitions(3)
        .replicas(3)
        .build()

    @Bean
    fun memberActivityTopic(): NewTopic = TopicBuilder
        .name("member-activity")
        .partitions(12)
        .replicas(3)
        .build()
}
