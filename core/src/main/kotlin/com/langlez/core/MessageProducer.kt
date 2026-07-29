package com.langlez.core

interface MessageProducer {

    /** 단일 토픽 발행 (key가 null이면 파티션 간 부하 분산 라우팅) */
    fun produce(
        topic: String,
        payload: String?,
        key: Any? = null,
    )

    /** 다중 토픽 발행 */
    fun produce(
        topics: List<String>,
        payload: String?,
        key: Any? = null,
    ) = topics.forEach { topic -> produce(topic, payload, key) }

}
