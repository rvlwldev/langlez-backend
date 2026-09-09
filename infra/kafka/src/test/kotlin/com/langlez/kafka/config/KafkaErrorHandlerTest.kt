package com.langlez.kafka.config

import com.fasterxml.jackson.core.JsonProcessingException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import org.springframework.classify.BinaryExceptionClassifier
import org.springframework.core.MethodParameter
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.ExceptionClassifier
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.kafka.support.SendResult
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException
import org.springframework.messaging.support.GenericMessage
import java.util.concurrent.CompletableFuture

class KafkaErrorHandlerTest : BehaviorSpec({

    fun setup(): Pair<KafkaTemplate<String, String>, DefaultErrorHandler> {
        val template = mockk<KafkaTemplate<String, String>>(relaxed = true)
        val future = CompletableFuture.completedFuture(mockk<SendResult<String, String>>(relaxed = true))
        every { template.send(any<ProducerRecord<String, String>>()) } returns future
        val errorHandler = KafkaConfiguration().kafkaErrorHandler(template)
        return template to errorHandler
    }

    fun DefaultErrorHandler.classify(ex: Throwable): Boolean {
        val method = ExceptionClassifier::class.java.getDeclaredMethod("getClassifier")
        method.isAccessible = true
        val classifier = method.invoke(this) as BinaryExceptionClassifier
        return classifier.classify(ex)
    }

    val consumer = mockk<Consumer<*, *>>(relaxed = true)
    val container = mockk<MessageListenerContainer>(relaxed = true)

    Given("DefaultErrorHandler 가 설정되었을 때") {

        When("Jackson JsonProcessingException 이 발생하면") {
            val (template, errorHandler) = setup()
            val record = ConsumerRecord("orders", 0, 0L, "key", """{"invalid" json}""")
            val jsonException = object : JsonProcessingException("Unexpected character") {}

            Then("재시도 불가(non-retryable) 예외로 분류된다") {
                errorHandler.classify(jsonException) shouldBe false
            }

            Then("재시도 없이 즉시 DLT 로 복구된다") {
                val recovered = errorHandler.handleOne(jsonException, record, consumer, container)
                recovered shouldBe true
                verify(exactly = 1) { template.send(any<ProducerRecord<String, String>>()) }
            }
        }

        When("Spring MessageConversionException 이 발생하면") {
            val (template, errorHandler) = setup()
            val record = ConsumerRecord("orders", 0, 0L, "key", "payload")
            val conversionException = MessageConversionException("Could not convert message")

            Then("재시도 불가(non-retryable) 예외로 분류된다") {
                errorHandler.classify(conversionException) shouldBe false
            }

            Then("재시도 없이 즉시 DLT 로 복구된다") {
                val recovered = errorHandler.handleOne(conversionException, record, consumer, container)
                recovered shouldBe true
                verify(exactly = 1) { template.send(any<ProducerRecord<String, String>>()) }
            }
        }

        When("리스너 실행 실패 예외(ListenerExecutionFailedException)로 감싸진 경우") {
            val (template, errorHandler) = setup()
            val record = ConsumerRecord("orders", 0, 0L, "key", "payload")
            val wrappedJsonEx = ListenerExecutionFailedException(
                "Listener failed",
                object : JsonProcessingException("malformed json payload") {},
            )
            val wrappedConversionEx = ListenerExecutionFailedException(
                "Listener failed",
                MessageConversionException("payload conversion failed"),
            )

            Then("언래핑되어 재시도 없이 즉시 DLT 로 복구된다") {
                errorHandler.handleOne(wrappedJsonEx, record, consumer, container) shouldBe true
                errorHandler.handleOne(wrappedConversionEx, record, consumer, container) shouldBe true
                verify(exactly = 2) { template.send(any<ProducerRecord<String, String>>()) }
            }
        }

        When("기타 역직렬화 관련 예외들이 발생하면") {
            val (template, errorHandler) = setup()
            val record = ConsumerRecord("orders", 0, 0L, "key", "payload")
            val serializationEx = SerializationException("serialization error")
            val deserializationEx = DeserializationException("deserialization error", byteArrayOf(), false, null)
            val dummyMethod = KafkaErrorHandlerTest::class.java.declaredMethods.first()
            val methodParameter = MethodParameter(dummyMethod, -1)
            val methodResolutionEx = MethodArgumentResolutionException(GenericMessage("test"), methodParameter)

            Then("모두 재시도 불가(non-retryable) 예외로 분류된다") {
                errorHandler.classify(serializationEx) shouldBe false
                errorHandler.classify(deserializationEx) shouldBe false
                errorHandler.classify(methodResolutionEx) shouldBe false
            }

            Then("재시도 없이 즉시 DLT 로 복구된다") {
                errorHandler.handleOne(serializationEx, record, consumer, container) shouldBe true
                errorHandler.handleOne(deserializationEx, record, consumer, container) shouldBe true
                errorHandler.handleOne(methodResolutionEx, record, consumer, container) shouldBe true
                verify(exactly = 3) { template.send(any<ProducerRecord<String, String>>()) }
            }
        }
    }
})
