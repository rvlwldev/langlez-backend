package com.langlez.kafka.config

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.core.task.VirtualThreadTaskExecutor
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
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
     * 리스너 컨테이너를 명시 선언한다.
     *
     * 오토컨피그 기본값은 컨슈머 스레드 1개라, 파티션을 늘려도 처리량이 안 늘어난다.
     * 리스너 작업은 대부분 DB/외부 호출 대기라 가상 스레드로 돌린다.
     * `concurrency` 는 파티션 수를 넘겨도 남는 컨슈머가 놀 뿐이라 3으로 잡는다.
     */
    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        errorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            this.consumerFactory = consumerFactory
            setConcurrency(3)
            setCommonErrorHandler(errorHandler)
            containerProperties.listenerTaskExecutor =
                VirtualThreadTaskExecutor("kafka-listener-")
        }

    /**
     * 컨슈머 예외 시 재시도 정책.
     *
     * 기본 `DefaultErrorHandler` 는 백오프 없이 10회를 몰아친다. 외부 API 나 DB 가 흔들려
     * 실패한 경우 그 10회가 순식간에 소진되고 메시지가 버려진다. 지수 백오프로 늘려 잡는다.
     *
     * 역직렬화 실패는 재시도해도 영원히 같은 결과라 즉시 건너뛴다. 안 그러면 그 파티션이
     * 문제 메시지 하나에 막혀 뒤가 전부 정체된다(poison pill).
     *
     * 재시도까지 실패한 메시지는 `<원본토픽>.DLT` 로 넘긴다. 로그만 남기면 recoverer 가
     * 정상 처리로 간주해 오프셋이 커밋되고 메시지는 그대로 사라져 되살릴 방법이 없다.
     */
    @Bean
    fun kafkaErrorHandler(template: KafkaTemplate<String, String>): DefaultErrorHandler {
        val backOff = ExponentialBackOff().apply {
            initialInterval = 1_000
            multiplier = 2.0
            maxInterval = 30_000
            maxElapsedTime = 300_000 // 5분까지 재시도 후 포기
        }

        val recoverer = DeadLetterPublishingRecoverer(template, ::dltDestination)

        // recoverer 를 람다로 감싸면 안 된다. DefaultErrorHandler 는 ConsumerAwareRecordRecoverer 일 때만
        // consumer 를 넘겨 파티션 유효성(verifyPartition)을 확인한다. 감싸면 그 가드가 꺼진다.
        // 로그는 RetryListener 로 남긴다.
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(SerializationException::class.java)
            setRetryListeners(object : RetryListener {
                override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception, deliveryAttempt: Int) = Unit

                override fun recovered(record: ConsumerRecord<*, *>, ex: Exception) {
                    logger.error(
                        "Kafka message handling finally failed, routing to DLT: topic={} partition={} offset={} key={}",
                        record.topic(), record.partition(), record.offset(), record.key(), ex,
                    )
                }
            })
        }
    }

    companion object {
        /**
         * DLT 목적지. 파티션을 -1 로 둬서 프로듀서가 분배하게 한다.
         * 소스 파티션 번호를 그대로 쓰면 DLT 파티션 수가 더 적을 때(auto-create 시 흔하다)
         * 발행이 실패하고, 실패한 레코드를 다시 seek 해서 무한 재시도에 빠진다.
         */
        fun dltDestination(record: ConsumerRecord<*, *>, @Suppress("UNUSED_PARAMETER") ex: Exception): TopicPartition =
            TopicPartition("${record.topic()}.DLT", -1)
    }
}
