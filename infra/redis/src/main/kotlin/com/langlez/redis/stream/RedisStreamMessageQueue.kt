package com.langlez.redis.stream

import com.langlez.core.MessageQueue
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamAddArgs
import org.springframework.stereotype.Component

@Component
class RedisStreamMessageQueue(private val redissonClient: RedissonClient) : MessageQueue {

    override fun publish(topic: String, key: String, payload: String) {
        redissonClient.getStream<String, String>(topic)
            .add(StreamAddArgs.entries(mapOf("key" to key, "payload" to payload)))
    }
}
