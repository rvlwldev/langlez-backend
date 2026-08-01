package com.langlez.kafka.config

import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.ExponentialBackOff

/**
 * 브로커 주소 / 직렬화 / acks 는 `application.yml` 의 `spring.kafka.*` 가 담당한다.
 * 여기서는 프로퍼티로 표현이 안 되는 것만 정의한다.
 *
 * 발행 실패 처리는 아웃박스가 맡는다. `OutBoxProcessor` 가 send 결과를 기다렸다가
 * 실패하면 이벤트를 FAILED 로 남겨 다음 스케줄에서 재발행한다.
 */
@Configuration
@EnableKafka
class KafkaConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 컨슈머 예외 시 재시도 정책.
     *
     * 기본 `DefaultErrorHandler` 는 백오프 없이 10회를 몰아친다. 외부 API 나 DB 가 흔들려
     * 실패한 경우 그 10회가 순식간에 소진되고 메시지가 버려진다. 지수 백오프로 늘려 잡는다.
     *
     * 역직렬화 실패는 재시도해도 영원히 같은 결과라 즉시 건너뛴다. 안 그러면 그 파티션이
     * 문제 메시지 하나에 막혀 뒤가 전부 정체된다(poison pill).
     */
    @Bean
    fun kafkaErrorHandler(): DefaultErrorHandler {
        val backOff = ExponentialBackOff().apply {
            initialInterval = 1_000
            multiplier = 2.0
            maxInterval = 30_000
            maxElapsedTime = 300_000 // 5분까지 재시도 후 포기
        }

        return DefaultErrorHandler({ record, e ->
            logger.error(
                "Kafka message handling finally failed: topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key(), e,
            )
        }, backOff).apply {
            addNotRetryableExceptions(SerializationException::class.java)
        }
    }
}
