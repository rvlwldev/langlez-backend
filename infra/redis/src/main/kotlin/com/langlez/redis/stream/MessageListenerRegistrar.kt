package com.langlez.redis.stream

import com.langlez.core.MessageConsumer
import com.langlez.core.MessageSemantic.ALO
import com.langlez.core.MessageSemantic.AMO
import com.langlez.redis.distributedLock.DistributedLock
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.redisson.api.RStream
import org.redisson.api.RedissonClient
import org.redisson.api.StreamMessageId
import org.redisson.api.stream.StreamCreateGroupArgs
import org.redisson.api.stream.StreamReadGroupArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import org.springframework.util.ReflectionUtils

/**
 * [MessageConsumer] 어노테이션이 부착된 메서드를 찾아 Redis Stream 컨슈머 그룹으로 자동 등록하고,
 * Java Virtual Thread(가상 스레드)를 이용해 비동기 폴링을 수행하는 등록기.
 *
 * 주요 기능:
 * 1. [SmartInitializingSingleton]: 모든 싱글톤 빈이 생성된 후 @MessageConsumer 메서드 탐색 및 컨슈머 가동.
 * 2. [DisposableBean]: 애플리케이션 종료 시 안전하게 폴링 루프를 멈추고 가상 스레드 무중단(Graceful Shutdown) 종료.
 * 3. [MessageSemantic] 연동:
 *    - AMO (At-Most-Once): 처리 여부와 관계없이 수신 직후 ACK 처리 (유실 허용, 중복 방지)
 *    - ALO (At-Least-Once): 비즈니스 핸들러 성공 시에만 ACK 처리 (유실 방지, 예외 발생 시 PEL 유지)
 * 4. PEL 복구 스케줄러 ([processPendingMessages]):
 *    - ALO 모드에서 처리 실패하여 레디스 PEL에 정체된 미승인 메시지를 [RStream.autoClaim]으로 주기적으로 자동 재처리.
 */
@Component
class MessageListenerRegistrar(
    private val redisson: RedissonClient,
    private val context: ApplicationContext,
    @param:Autowired(required = false) private val configurer: RedisStreamConfigurer? = null,
) : SmartInitializingSingleton, DisposableBean {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** 컨슈머 폴링 루프의 실행 여부를 제어하는 플래그 (destroy 호출 시 false로 전환) */
    private val running = AtomicBoolean(true)

    /** I/O 블로킹 무부담 폴링을 위해 Java Virtual Thread 기반 실행자 사용 */
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    /** 인스턴스(서버 인스턴스)별 고유한 Consumer 식별 이름 생성 */
    private val instanceName = "instance-${UUID.randomUUID()}"

    /** 등록된 컨슈머 목록 (PEL 복구 스케줄러에서 활용) */
    private val consumers = CopyOnWriteArrayList<Consumer>()

    private data class Consumer(val bean: Any, val method: Method, val listener: MessageConsumer, val topic: String)

    override fun afterSingletonsInstantiated() {
        context.beanDefinitionNames.forEach { name ->
            val bean = runCatching { context.getBean(name) }.getOrNull() ?: return@forEach
            val clazz = ClassUtils.getUserClass(bean)

            ReflectionUtils.doWithMethods(clazz) { method ->
                val listener = AnnotationUtils.findAnnotation(method, MessageConsumer::class.java)
                    ?: return@doWithMethods

                val topics = listener.topics.toList()

                topics.forEach { topic ->
                    val partitions = configurer?.getPartitions(topic) ?: 1
                    if (partitions > 1)
                        for (p in 0 until partitions)
                            startConsumer(bean, method, listener, "$topic-$p")
                    else startConsumer(bean, method, listener, topic)
                }
            }
        }
    }

    private fun startConsumer(bean: Any, method: Method, listener: MessageConsumer, topic: String) {
        method.trySetAccessible()

        // PEL 재처리를 위해 컨슈머 정보 등록
        consumers.add(Consumer(bean, method, listener, topic))

        // Redis Stream Consumer Group 생성 (이미 존재하는 경우 BUSYGROUP 무시)
        val stream = redisson.getStream<String, String>(topic)
        runCatching { stream.createGroup(StreamCreateGroupArgs.name(listener.group).makeStream()) }
            .onFailure { if (it.message?.contains("BUSYGROUP") != true) throw it }

        // 가상 스레드 생성을 통해 폴링 작업 비동기 제출
        executor.submit {
            val thread = Thread.currentThread()
            val originalName = thread.name
            thread.name = "message-listener-$topic-${listener.group}"

            try {
                while (running.get()) {
                    runCatching { pollAndProcessMessages(stream, bean, method, listener, topic) }
                        .onFailure { e ->
                            when (e) {
                                is InterruptedException -> {
                                    Thread.currentThread().interrupt()
                                    running.set(false)
                                }

                                else -> {
                                    if (running.get()) {
                                        logger.error("Redis Stream read failed: topic=$topic", e)
                                        runCatching { Thread.sleep(1000) }
                                            .onFailure {
                                                Thread.currentThread().interrupt()
                                                running.set(false)
                                            }
                                    }
                                }
                            }
                        }
                }
            } finally {
                thread.name = originalName
            }
        }

        logger.info("Registered message listener: topic=$topic group=${listener.group} semantic=${listener.semantic}")
    }

    private fun pollAndProcessMessages(
        stream: RStream<String, String>,
        bean: Any,
        method: Method,
        listener: MessageConsumer,
        topic: String,
    ) {
        val messages = stream.readGroup(
            listener.group,
            instanceName,
            StreamReadGroupArgs.neverDelivered().count(10).timeout(Duration.ofSeconds(5)),
        )

        messages.forEach { (id, fields) ->
            val payload = fields["payload"]

            when (listener.semantic) {
                AMO -> {
                    stream.ack(listener.group, id)
                    runCatching { method.invoke(bean, payload) }
                        .onFailure { e ->
                            val cause = (e as? InvocationTargetException)?.targetException ?: e
                            logger.error(
                                "Message Listener handling failed: semantic=AMO topic=$topic messageId=$id",
                                cause
                            )
                        }
                }

                ALO -> {
                    runCatching { method.invoke(bean, payload) }
                        .onSuccess { stream.ack(listener.group, id) }
                        .onFailure { e ->
                            val cause = (e as? InvocationTargetException)?.targetException ?: e
                            logger.error(
                                "Message Listener handling failed: semantic=ALO topic=$topic messageId=$id",
                                cause
                            )
                        }
                }
            }
        }
    }

    /**
     * ALO 모드에서 예외 발생 등으로 ACK 받지 못하고 PEL(Pending Entries List)에 방치된 메시지를 1초마다 자동 재처리(autoClaim)한다.
     */
    @Scheduled(cron = "*/1 * * * * *")
    @DistributedLock(prefix = "lock:stream-message-recovery", throwOnFailure = false)
    fun processPendingMessages() {
        if (!running.get() || consumers.isEmpty()) return

        consumers.filter { it.listener.semantic == ALO }.forEach { consumer ->
            runCatching {
                val stream = redisson.getStream<String, String>(consumer.topic)
                val claimResult = stream.autoClaim(
                    consumer.listener.group,
                    instanceName,
                    1,
                    TimeUnit.SECONDS,
                    StreamMessageId.MIN,
                    50
                )

                claimResult.messages.forEach { (id, fields) ->
                    val payload = fields["payload"]
                    runCatching { consumer.method.invoke(consumer.bean, payload) }
                        .onSuccess {
                            stream.ack(consumer.listener.group, id)
                            logger.debug(
                                "Successfully recovered pending message: topic={} id={} group={}",
                                consumer.topic,
                                id,
                                consumer.listener.group
                            )
                        }
                        .onFailure { e ->
                            val cause = (e as? InvocationTargetException)?.targetException ?: e
                            logger.error(
                                "Pending Message recovery retry failed: topic=${consumer.topic} id=$id group=${consumer.listener.group}",
                                cause
                            )
                        }
                }
            }.onFailure { e ->
                logger.warn(
                    "Failed to check pending messages: topic=${consumer.topic} group=${consumer.listener.group}",
                    e
                )
            }
        }
    }

    override fun destroy() {
        running.set(false)
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("MessageListenerRegistrar executor did not terminate in 5s, forcing shutdown")
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
