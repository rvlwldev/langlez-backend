package com.langlez.redis.broadcast

import com.langlez.core.MessageBroadcaster
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.redisson.api.RedissonClient
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * 다중 인스턴스 팬아웃 브로드캐스터.
 *
 * STOMP 의 simple broker 는 인메모리다. `convertAndSend` 는 같은 JVM 에 붙은 세션에만 닿고,
 * 다른 인스턴스에 연결된 사용자는 아무 오류 없이 조용히 못 받는다.
 * 그래서 발행은 전부 레디스 채널로 내보내고, 모든 인스턴스가 그 채널을 구독해
 * 각자 자기 세션으로 밀어준다.
 */
@Component
class RedisMessageBroadcaster(
    redisson: RedissonClient,
    private val stomp: SimpMessagingTemplate,
) : MessageBroadcaster {

    private val channel = redisson.getTopic(CHANNEL)
    private var listenerId: Int? = null

    @PostConstruct
    fun subscribe() {
        listenerId = channel.addListener(BroadcastEnvelope::class.java) { _, envelope ->
            stomp.convertAndSend(envelope.topic, envelope.payload)
        }
    }

    /** 해제하지 않으면 종료 중인 인스턴스가 계속 수신해 이미 끊긴 세션으로 밀어댄다. */
    @PreDestroy
    fun unsubscribe() {
        listenerId?.let { channel.removeListener(it) }
        listenerId = null
    }

    // 발행자 자신도 구독을 통해 되받는다. 여기서 stomp 로 직접 보내면 로컬 구독자만 두 번 받는다.
    override fun broadcast(topic: String, payload: Any) {
        channel.publish(BroadcastEnvelope(topic, payload))
    }

    companion object {
        private const val CHANNEL = "broadcast"
    }
}
