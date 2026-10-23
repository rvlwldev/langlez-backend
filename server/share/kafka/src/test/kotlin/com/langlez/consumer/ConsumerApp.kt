package com.langlez.consumer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@SpringBootApplication
class ConsumerApp

@Component
class DemoListener {

    val received = CopyOnWriteArrayList<String>()

    @KafkaListener(topics = ["demo"], groupId = "demo-group")
    fun on(message: String) {
        if (message == "bad") throw MessageConversionException("변환 실패")
        received += message
    }
}
