package com.langlez.core

/**
 * 토픽 메타데이터(파티션 개수, 시멘틱 등)를 정의하는 구성 객체.
 * @Configuration 클래스에서 @Bean으로 등록하면 RedisStreamConfigurer가 이를 수집하여 파티션 및 구성을 자동 적용한다.
 */
data class MessageTopic(
    val name: String,
    val partitions: Int = 1,
    val semantic: MessageSemantic = MessageSemantic.ALO,
)
