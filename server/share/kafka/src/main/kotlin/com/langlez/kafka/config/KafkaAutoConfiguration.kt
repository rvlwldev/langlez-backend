package com.langlez.kafka.config

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.core.task.VirtualThreadTaskExecutor
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException
import org.springframework.util.backoff.ExponentialBackOff
import tools.jackson.core.JacksonException

@AutoConfiguration(beforeName = ["org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"])
@ConditionalOnClass(KafkaTemplate::class)
class KafkaAutoConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean(name = ["kafkaListenerContainerFactory"])
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        consumerFactory: ConsumerFactory<Any, Any>,
        errorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> =
        ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            configurer.configure(this, consumerFactory)
            setCommonErrorHandler(errorHandler)
            containerProperties.listenerTaskExecutor = VirtualThreadTaskExecutor("kafka-listener-")
        }

    @Bean
    @ConditionalOnMissingBean
    fun kafkaErrorHandler(template: KafkaTemplate<String, String>): DefaultErrorHandler {
        val backOff = ExponentialBackOff().apply {
            initialInterval = 1_000
            multiplier = 2.0
            maxInterval = 30_000
            maxElapsedTime = 300_000
        }

        val recoverer = DeadLetterPublishingRecoverer(template, ::dltDestination)

        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(
                SerializationException::class.java,
                JacksonException::class.java,
                MessageConversionException::class.java,
                DeserializationException::class.java,
                MethodArgumentResolutionException::class.java,
            )
            setRetryListeners(object : RetryListener {
                override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception?, deliveryAttempt: Int) = Unit

                override fun recovered(record: ConsumerRecord<*, *>, ex: Exception?) {
                    logger.error(
                        "Kafka 메시지 처리에 끝내 실패해 DLT 로 보낸다: topic={} partition={} offset={} key={}",
                        record.topic(), record.partition(), record.offset(), record.key(), ex,
                    )
                }
            })
        }
    }

    companion object {
        fun dltDestination(record: ConsumerRecord<*, *>, ex: Exception): TopicPartition =
            TopicPartition("${record.topic()}.DLT", -1)
    }
}
