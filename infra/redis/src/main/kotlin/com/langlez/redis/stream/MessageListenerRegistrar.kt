package com.langlez.redis.stream

import com.langlez.core.MessageListener
import java.lang.reflect.Method
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamCreateGroupArgs
import org.redisson.api.stream.StreamReadGroupArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import org.springframework.util.ReflectionUtils

/** [MessageListener]가 붙은 메서드를 찾아 Redis Stream 컨슈머 그룹으로 등록하고, 가상 스레드로 폴링한다. */
@Component
class MessageListenerRegistrar(
    private val redissonClient: RedissonClient,
    private val applicationContext: ApplicationContext,
) : SmartInitializingSingleton, DisposableBean {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(true)
    private val threads = mutableListOf<Thread>()
    private val consumerName = "consumer-${UUID.randomUUID()}"

    override fun afterSingletonsInstantiated() {
        applicationContext.beanDefinitionNames.forEach { name ->
            val bean = runCatching { applicationContext.getBean(name) }.getOrNull() ?: return@forEach
            val targetClass = ClassUtils.getUserClass(bean)

            ReflectionUtils.doWithMethods(targetClass) { method ->
                val listener = AnnotationUtils.findAnnotation(method, MessageListener::class.java) ?: return@doWithMethods
                startConsumer(bean, method, listener)
            }
        }
    }

    private fun startConsumer(bean: Any, method: Method, listener: MessageListener) {
        method.trySetAccessible()
        val stream = redissonClient.getStream<String, String>(listener.topic)

        runCatching { stream.createGroup(StreamCreateGroupArgs.name(listener.group).makeStream()) }
            .onFailure { if (it.message?.contains("BUSYGROUP") != true) throw it }

        val thread = Thread.ofVirtual()
            .name("msg-listener-${listener.topic}-${listener.group}")
            .start {
                while (running.get()) {
                    try {
                        val messages = stream.readGroup(
                            listener.group,
                            consumerName,
                            StreamReadGroupArgs.neverDelivered().count(10).timeout(Duration.ofSeconds(5)),
                        )
                        messages.forEach { (id, fields) ->
                            try {
                                method.invoke(bean, fields["payload"])
                                stream.ack(listener.group, id)
                            } catch (e: Exception) {
                                logger.error("Message handling failed: topic=${listener.topic} id=$id", e)
                            }
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (e: Exception) {
                        if (running.get()) {
                            logger.warn("Stream read failed: topic=${listener.topic}", e)
                            Thread.sleep(1000)
                        }
                    }
                }
            }

        threads += thread
        logger.info("Registered message listener: topic=${listener.topic} group=${listener.group}")
    }

    override fun destroy() {
        running.set(false)
        threads.forEach { it.interrupt() }
    }
}
