package com.langlez.redis.distributedLock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 분산 락 AOP Aspect */
@Aspect
@Component
@Order(1)
class DistributedLockAspect(private val service: RedisLockService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Pointcut("@annotation(com.langlez.redis.distributedLock.DistributedLock)")
    fun distributedLockPointcut() {}

    @Around("distributedLockPointcut() && @annotation(distributedLock)")
    @Transactional
    fun lock(point: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
        val lockKeys = extractLockKeys(point)
        val lockName = generateLockKey(distributedLock.prefix, lockKeys)

        logger.debug("Attempting to acquire lock: $lockName")

        // 락 획득 시도 (재시도 로직 포함)
        val count = distributedLock.retryCount
        val interval = distributedLock.retryInterval

        return try {
            var acquired = false

            for (attempt in 1..count) {
                if (service.acquireLock(lockName, distributedLock.expirationTime)) {
                    logger.debug("Lock acquired successfully: $lockName (attempt $attempt)")
                    acquired = true
                    break
                }

                if (attempt < count) {
                    logger.debug(
                            "Lock acquisition failed: $lockName (attempt $attempt), retrying in ${interval}ms..."
                    )
                    Thread.sleep(interval)
                }
            }

            if (!acquired) {
                logger.error("Lock acquisition failed after $count attempts: $lockName")
                throw IllegalStateException(
                        "Lock acquisition failed for key: $lockName after $count attempts"
                )
            }

            point.proceed()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } finally {
            // 락 해제
            try {
                service.releaseLock(lockName)
                logger.debug("Lock released: $lockName")
            } catch (e: Exception) {
                logger.warn("Failed to release lock: $lockName", e)
            }
        }
    }

    /** 메서드 파라미터에서 @LockKey 어노테이션이 붙은 값들을 추출 (파라미터 이름 순 정렬) */
    private fun extractLockKeys(joinPoint: ProceedingJoinPoint): List<String> {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val parameterAnnotations = method.parameterAnnotations
        val args = joinPoint.args
        val parameterNames = signature.parameterNames ?: Array(args.size) { "arg$it" }

        val keys = mutableListOf<Pair<String, String>>()
        for (i in args.indices) if (parameterAnnotations[i].any { it is LockKey })
                keys.add(parameterNames[i] to (args[i]?.toString() ?: "null"))

        // 키가 있으면 이름 순으로 정렬하여 값 반환
        if (keys.isNotEmpty()) return keys.sortedBy { it.first }.map { it.second }

        // 만약 @LockKey가 하나도 없다면 메서드 이름 자체를 락 키로 사용 (메서드 단위 락)
        return listOf(method.declaringClass.name, method.name)
    }
    /** 접두사와 추출된 키들을 조합하여 최종 락 키 생성 */
    private fun generateLockKey(prefix: String, keys: List<String>): String =
            "$prefix${keys.joinToString(":")}"
}
