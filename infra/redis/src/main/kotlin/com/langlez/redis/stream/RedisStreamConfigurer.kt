package com.langlez.redis.stream

import com.langlez.core.MessageTopic
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class RedisStreamConfigurer(private val context: ApplicationContext) {
    private val topicMap: Map<String, MessageTopic> by lazy {
        context.getBeansOfType(MessageTopic::class.java).values.associateBy { it.name }
    }

    fun getTopic(name: String): MessageTopic? = topicMap[name]
    fun getPartitions(topic: String): Int = topicMap[topic]?.partitions ?: 1
}
