package com.langlez.redis.stream

import com.langlez.core.MessageQueueTopic
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component
class RedisStreamConfigurer(private val context: ApplicationContext) {
    private val topicMap: Map<String, MessageQueueTopic> by lazy {
        context.getBeansOfType(MessageQueueTopic::class.java).values.associateBy { it.name }
    }

    fun getTopic(name: String): MessageQueueTopic? = topicMap[name]
    fun getPartitions(topic: String): Int = topicMap[topic]?.partitions ?: 1
}
