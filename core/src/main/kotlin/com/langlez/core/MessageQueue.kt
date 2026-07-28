package com.langlez.core

interface MessageQueue {

    /** 단일 토픽 발행 (key가 null이면 파티션 간 부하 분산 라우팅) */
    fun publish(
        topic: String,
        payload: String?,
        key: Any? = null,
    )

    /** 다중 토픽 발행 */
    fun publish(
        topics: List<String>,
        payload: String?,
        key: Any? = null,
    ) = topics.forEach { topic -> publish(topic, payload, key) }

}
