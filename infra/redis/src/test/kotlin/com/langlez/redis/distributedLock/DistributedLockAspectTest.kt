package com.langlez.redis.distributedLock

import io.mockk.*
import java.util.concurrent.TimeUnit
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class DistributedLockAspectTest {

    private val redisLockService: RedisLockService = mockk(relaxed = true)
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)
    private val parameterNameDiscoverer: ParameterNameDiscoverer = mockk(relaxed = true)
    private val aspect =
            DistributedLockAspect(redisLockService, transactionManager, parameterNameDiscoverer)
    private val joinPoint: ProceedingJoinPoint = mockk(relaxed = true)
    private val signature: MethodSignature = mockk(relaxed = true)
    private val transactionStatus: TransactionStatus = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        clearMocks(
                redisLockService,
                transactionManager,
                parameterNameDiscoverer,
                joinPoint,
                signature
        )
        every { joinPoint.signature } returns signature
        every { transactionManager.getTransaction(any()) } returns transactionStatus

        // Mock executeWithLock to simply execute the block
        every {
            redisLockService.executeWithLock<Any?>(any(), any(), any(), any(), captureLambda())
        } answers { lambda<() -> Any?>().invoke() }
    }

    @Test
    fun `should delegate to RedisLockService with correct parameters`() {
        // Given
        val method = TestService::class.java.getMethod("simpleLock")
        every { signature.method } returns method
        every { signature.parameterNames } returns emptyArray()
        every { joinPoint.args } returns emptyArray()

        val annotation = method.getAnnotation(DistributedLock::class.java)
        val expectedLockName = "lock:${TestService::class.java.name}:simpleLock"

        // When
        aspect.lock(joinPoint, annotation)

        // Then
        verify(exactly = 1) {
            redisLockService.executeWithLock(
                    expectedLockName,
                    1000L,
                    10000L,
                    TimeUnit.MILLISECONDS,
                    any()
            )
        }
        verify(exactly = 1) { joinPoint.proceed() }
    }

    @Test
    fun `should parse SpEL keys correctly`() {
        // Given
        val method = TestService::class.java.getMethod("spelLock", TestDto::class.java)
        val dto = TestDto("test-id", "test-type")
        every { signature.method } returns method
        every { signature.parameterNames } returns arrayOf("dto")
        every { joinPoint.args } returns arrayOf(dto)
        every { parameterNameDiscoverer.getParameterNames(method) } returns arrayOf("dto")

        val annotation = method.getAnnotation(DistributedLock::class.java)

        // When
        aspect.lock(joinPoint, annotation)

        // Then
        verify(exactly = 1) {
            redisLockService.executeWithLock("lock:test-type:test-id", any(), any(), any(), any())
        }
    }

    @Test
    fun `should execute within transaction when transactional is true`() {
        // Given
        val method = TestService::class.java.getMethod("transactionalLock")
        every { signature.method } returns method
        every { signature.parameterNames } returns emptyArray()
        every { joinPoint.args } returns emptyArray()

        val annotation = method.getAnnotation(DistributedLock::class.java)

        // When
        aspect.lock(joinPoint, annotation)

        // Then
        verify(exactly = 1) { transactionManager.getTransaction(any()) }
        verify(exactly = 1) { transactionManager.commit(any()) }
    }

    class TestService {
        @DistributedLock fun simpleLock() {}

        @DistributedLock(keys = ["#dto.type", "#dto.id"]) fun spelLock(dto: TestDto) {}

        @DistributedLock(transactional = true) fun transactionalLock() {}
    }

    data class TestDto(val id: String, val type: String)
}
