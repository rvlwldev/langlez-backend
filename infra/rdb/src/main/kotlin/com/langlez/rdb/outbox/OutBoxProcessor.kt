package com.langlez.rdb.outbox

import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS

abstract class OutBoxProcessor<T : OutBox>(private val repo: OutBoxRepository<T>) : DisposableBean {
    open val chunk = 100
    open val tries = 3
    open val threads = 8
    open val kafkaTimeout = 5L
    open val threadTimeout = 10L

    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val semaphore = Semaphore(threads)

    @Autowired
    private lateinit var kafka: KafkaTemplate<String, String>

    open fun send() = repo.fetch(chunk, tries)
        .ifEmpty { return }
        .map { outbox -> executor.submit { send(outbox) } }
        .forEach { runCatching { it.get(threadTimeout, SECONDS) } }

    private fun send(outbox: T) {
        semaphore.acquire()

        try {
            runCatching { outbox.dispatch() }
                .onFailure {
                    outbox.fail(tries)
                    repo.save(outbox)
                    return
                }

            runCatching { kafka.send(outbox.record).get(kafkaTimeout, SECONDS) }
                .onSuccess { outbox.complete() }
                .onFailure { outbox.fail(tries) }

            repo.save(outbox)
        } finally {
            semaphore.release()
        }
    }

    override fun destroy() {
        runCatching {
            executor.shutdown()

            if (!executor.awaitTermination(5, SECONDS))
                executor.shutdownNow()
        }
    }

    private val OutBox.record: ProducerRecord<String, String>
        get() = ProducerRecord(topic, key, payload)
}


