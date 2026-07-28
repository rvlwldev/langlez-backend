package com.langlez.redis.stream

import com.langlez.core.MessageListener
import com.langlez.core.MessageSemantic
import com.langlez.core.MessageSemantic.ALO
import com.langlez.core.MessageSemantic.AMO
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.redisson.api.RStream
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamCreateGroupArgs
import org.redisson.api.stream.StreamReadGroupArgs
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import org.springframework.util.ReflectionUtils

/**
 * [MessageListener] 어노테이션이 부착된 메서드를 찾아 Redis Stream 컨슈머 그룹으로 자동 등록하고,
 * Java Virtual Thread(가상 스레드)를 이용해 비동기 폴링을 수행하는 등록기.
 *
 * 주요 기능:
 * 1. [SmartInitializingSingleton]: 모든 싱글톤 빈이 생성된 후 @MessageListener 메서드 탐색 및 컨슈머 가동.
 * 2. [DisposableBean]: 애플리케이션 종료 시 안전하게 폴링 루프를 멈추고 가상 스레드 무중단(Graceful Shutdown) 종료.
 * 3. [MessageSemantic] 연동:
 *    - AMO (At-Most-Once): 처리 여부와 관계없이 수신 직후 ACK 처리 (유실 허용, 중복 방지)
 *    - ALO (At-Least-Once): 비즈니스 핸들러 성공 시에만 ACK 처리 (유실 방지, 예외 발생 시 PEL 유지)
 * 4. 단일([MessageListener.topic])/다중([MessageListener.topics]) 토픽 및 파티션([RedisStreamConfigurer]) 자동 가동:
 *    - @MessageListener에 지정된 토픽 목록별로 등록하며, Configurer에 파티션 개수(partitions > 1)가 등록되어 있는 경우 파티션 서브 스트림(`topic-0`, `topic-1`...)으로 각각 컨슈머를 자동 가동한다.
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

    /**
     * 모든 싱글톤 빈 생성이 완료된 후 Spring 수명주기에 의해 호출된다.
     * 컨텍스트 내의 모든 빈을 검사하여 @MessageListener 어노테이션이 선언된 메서드를 찾아 컨슈머를 시작한다.
     * 단일/다중 토픽 및 토픽별 파티션 개수를 [RedisStreamConfigurer]에서 조회하여 자동 등록한다.
     */
    override fun afterSingletonsInstantiated() {
        context.beanDefinitionNames.forEach { name ->
            val bean = runCatching { context.getBean(name) }.getOrNull() ?: return@forEach
            val clazz = ClassUtils.getUserClass(bean)

            ReflectionUtils.doWithMethods(clazz) { method ->
                val listener = AnnotationUtils.findAnnotation(method, MessageListener::class.java)
                    ?: return@doWithMethods

                val topics = if (listener.topics.isNotEmpty()) listener.topics.toList()
                else if (listener.topic.isNotBlank()) listOf(listener.topic)
                else emptyList()

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

    /**
     * 특정 파티션/토픽 스트림을 Redis Stream 컨슈머 루프에 등록하고 폴링 스레드를 실행한다.
     *
     * @param bean 대상 메서드가 정의된 Spring Bean 객체
     * @param method @MessageListener가 붙은 실행 대상 메서드
     * @param listener 메타데이터(topic, topics, group, semantic)를 담은 어노테이션 객체
     * @param topic 실제로 수신할 Redis Stream 토픽 이름 (단일 토픽 또는 파티션 토픽 `topic-N`)
     */
    private fun startConsumer(bean: Any, method: Method, listener: MessageListener, topic: String) {
        method.trySetAccessible()

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
                                    Thread.currentThread().interrupt() // 스레드 인터럽트 시 즉시 루프 종료
                                    running.set(false)
                                }

                                else -> {
                                    if (running.get()) {
                                        logger.warn("Redis Stream read failed: topic=$topic", e)
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

    /**
     * Consumer Group에서 신규 메시지를 폴링하고 [MessageSemantic] 정책(AMO/ALO)에 따라 비즈니스 메서드를 호출한다.
     */
    private fun pollAndProcessMessages(
        stream: RStream<String, String>,
        bean: Any,
        method: Method,
        listener: MessageListener,
        topic: String,
    ) {
        val messages = stream.readGroup(
            listener.group,
            instanceName,
            StreamReadGroupArgs.neverDelivered().count(10).timeout(Duration.ofSeconds(5)), // 10개씩 polling, 없으면 5초동안 기다림
        )

        messages.forEach { (id, fields) ->
            val payload = fields["payload"]

            when (listener.semantic) {
                AMO -> {
                    stream.ack(listener.group, id)
                    runCatching { method.invoke(bean, payload) }
                        .onFailure { e ->
                            val cause = (e as? InvocationTargetException)?.targetException ?: e
                            logger.error("Message Listener handling failed: topic=$topic id=$id semantic=AMO", cause)
                        }
                }

                ALO -> {
                    runCatching { method.invoke(bean, payload) }
                        .onSuccess { stream.ack(listener.group, id) }
                        .onFailure { e ->
                            val cause = (e as? InvocationTargetException)?.targetException ?: e
                            logger.error("Message Listener handling failed: topic=$topic id=$id semantic=ALO", cause)
                        }
                }
            }
        }
    }

    /**
     * 애플리케이션 종료 시 Spring 수명주기에 의해 호출된다.
     * 폴링 루프를 중단하고 잔여 작업 마무리를 최대 5초간 대기(Graceful Shutdown)한 후 강제 종료한다.
     */
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
