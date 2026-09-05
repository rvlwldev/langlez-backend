package com.langlez.kafka.config

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
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
     * 리스너 컨테이너를 명시 선언한다. 리스너 작업은 대부분 DB/외부 호출 대기라 가상 스레드로 돌린다.
     *
     * **`configurer.configure()` 를 반드시 먼저 부른다. 빼면 `spring.kafka.listener.*` 가 통째로 버려진다.**
     *
     * 이 빈의 이름이 정확히 `kafkaListenerContainerFactory` 라서, 같은 이름에
     * `@ConditionalOnMissingBean` 을 건 오토컨피그(`KafkaAnnotationDrivenConfiguration`)가 백오프한다.
     * 그런데 `spring.kafka.listener.*` 를 컨테이너로 옮기는 코드가 그 오토컨피그가 부르던
     * `configure()` 안에만 있다. 그래서 configure 를 안 부르면 yml 이 파싱은 되지만 아무 데도 안 닿고,
     * `ackMode` 는 기본값 `BATCH` 로 남는다 — 컴파일도 테스트도 통과하고 런타임에만 어긋난다.
     * 실제로 `ack-mode: record` 가 이 이유로 오랫동안 무시됐다.
     *
     * 순서가 중요하다. `configure()` 는 `PropertyMapper.alwaysApplyingWhenNonNull()` 이라
     * **값이 있는 것은 전부 덮어쓴다** — `concurrency`, `commonErrorHandler`, `listenerTaskExecutor` 포함.
     * 그러니 우리가 고정해야 하는 것은 configure 뒤에 얹는다.
     *
     * - `concurrency` 는 `application.yml`(`spring.kafka.listener.concurrency: 3`)로 옮겼다.
     *   **코드로 되돌리지 마라.** 여기서 `setConcurrency` 를 부르면 값이 configure 뒤에 얹혀
     *   yml 이 다시 조용히 무시된다 — 이 클래스가 방금 고친 결함과 정확히 같은 모양이다.
     *   대신 반대쪽 위험을 진다: **yml 에서 그 줄이 빠지면 경고 없이 기본값 1 로 폴백한다.**
     *   `KafkaProperties.Listener.concurrency` 기본값이 null 이라 configure 가 setter 를 아예
     *   안 부르고, 컨테이너 필드 초기값 1 이 그대로 남는다. 기동 로그에 아무 표시가 없다.
     *   파티션 3개짜리 토픽(`member-created` 등)에 스레드가 1개만 붙어 컨슘 지연만 쌓인다.
     *   새 프로필(`application-staging.yml` 등)을 만들며 `spring.kafka.listener` 블록을
     *   재정의하거나 빠뜨리면 그렇게 된다. 프로필을 추가하면 이 키가 살아있는지 확인해라.
     * - `commonErrorHandler` 는 configure 도 `CommonErrorHandler` 빈에서 채우지만 `getIfUnique()` 라,
     *   같은 타입 빈이 둘이 되면 조용히 null 이 되어 재시도·DLT 배선이 통째로 사라진다. 명시로 못 박는다.
     * - `listenerTaskExecutor` 는 프로퍼티로 표현이 안 된다. 게다가 `spring.threads.virtual.enabled` 를
     *   켜면 configure 가 자기 executor 로 갈아끼우므로 반드시 뒤에서 다시 넣는다.
     */
    @Bean
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        consumerFactory: ConsumerFactory<Any, Any>,
        errorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> =
        ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            configurer.configure(this, consumerFactory)

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
