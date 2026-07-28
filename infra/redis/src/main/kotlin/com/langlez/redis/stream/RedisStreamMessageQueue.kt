package com.langlez.redis.stream

import com.langlez.core.MessageQueue
import com.langlez.core.MessageSemantic
import java.util.concurrent.ThreadLocalRandom
import org.redisson.api.RedissonClient
import org.redisson.api.StreamMessageId
import org.redisson.api.stream.StreamAddArgs
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RedisStreamMessageQueue(
    private val redisson: RedissonClient,
    @param:Autowired(required = false) private val configurer: RedisStreamConfigurer? = null,
) : MessageQueue {

    override fun publish(topic: String, payload: String?, key: Any?) {
        val config = configurer?.getTopic(topic)
        val partitions = config?.partitions ?: 1
        val semantic = config?.semantic ?: MessageSemantic.ALO
        val keyString = key?.toString() ?: ""
        val payloadString = payload ?: ""

        val targetTopic = if (partitions > 1) {
            val partitionId = if (keyString.isNotBlank()) (keyString.hashCode() and Int.MAX_VALUE) % partitions
            else ThreadLocalRandom.current().nextInt(partitions)

            "$topic:$partitionId"
        } else topic


        val stream = redisson.getStream<String, String>(targetTopic)
        val entries = mapOf(
            "key" to keyString,
            "payload" to payloadString,
            "semantic" to semantic.name,
        )

        val minIdTime = System.currentTimeMillis() - RETENTION_PERIOD_MS
        val minId = StreamMessageId(minIdTime, 0)

        val addArgs = StreamAddArgs.entries(entries)
            .trim()
            .minId(minId)
            .noLimit()

        stream.add(addArgs)
    }

    companion object {
        private const val RETENTION_PERIOD_MS = 24 * 60 * 60 * 1000L // 24시간
    }
}
