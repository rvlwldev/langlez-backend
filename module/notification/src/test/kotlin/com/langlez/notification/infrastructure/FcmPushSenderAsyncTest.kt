package com.langlez.notification.infrastructure

import com.langlez.notification.TestNotificationApplication
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.stereotype.Component
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [TestNotificationApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.threads.virtual.enabled=true"
    ]
)
class FcmPushSenderAsyncTest : FunSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var asyncTestComponent: AsyncTestComponent

    companion object {
        @JvmField
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
        }
    }

    init {
        test("Spring @Async should execute on a Virtual Thread") {
            val callerThread = Thread.currentThread()
            var executedThread: Thread? = null
            var isVirtualThread: Boolean? = null
            val latch = CountDownLatch(1)

            asyncTestComponent.runAsync {
                executedThread = Thread.currentThread()
                isVirtualThread = Thread.currentThread().isVirtual
                latch.countDown()
            }

            val completed = latch.await(3, TimeUnit.SECONDS)
            completed shouldBe true
            executedThread shouldNotBe null
            executedThread shouldNotBe callerThread
            isVirtualThread shouldBe true
        }
    }
}

@Component
open class AsyncTestComponent {
    @org.springframework.scheduling.annotation.Async
    open fun runAsync(action: () -> Unit) {
        action()
    }
}
