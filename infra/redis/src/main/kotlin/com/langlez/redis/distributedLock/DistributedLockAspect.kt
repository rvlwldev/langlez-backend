package com.langlez.redis.distributedLock

import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.core.annotation.Order
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** 분산 락 AOP Aspect */
@Aspect
@Component
@Order(1)
class DistributedLockAspect(
    private val redisLockService: RedisLockService,
    private val transactionManager: PlatformTransactionManager,
    private val paramDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val parser: ExpressionParser = SpelExpressionParser()

    @Around("@annotation(distributedLock)")
    fun lock(point: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
        val methodSignature = point.signature as MethodSignature
        val method = methodSignature.method
        val args = point.args

        val lockKeys = mutableListOf<String>().apply {
            if (distributedLock.keys.isNotEmpty()) addAll(parseSpELKeys(method, args, distributedLock.keys))
            addAll(extractAnnotatedKeys(method, args, methodSignature.parameterNames))
            if (isEmpty()) add(method.declaringClass.name + ":" + method.name)
        }

        val lockName = "${distributedLock.prefix}${lockKeys.joinToString(":")}"
        val waitTime = distributedLock.retries * distributedLock.waitMs
        val leaseTime = distributedLock.leaseSecs * 1000

        logger.debug("Attempting to acquire lock: $lockName (wait: ${waitTime}ms, lease: ${leaseTime}ms)")

        return redisLockService.executeWithLock(
            lockName,
            waitTime,
            leaseTime,
            TimeUnit.MILLISECONDS,
            distributedLock.throwOnFailure
        ) {
            if (distributedLock.transactional) TransactionTemplate(transactionManager).execute { point.proceed() }
            else point.proceed()
        }
    }

    /**
     * SpEL 표현식 파싱하여 값 추출.
     *
     * 실패를 삼키면 키가 비어 메서드 전역 락으로 떨어진다. 엔티티별로 걸려던 락이
     * 조용히 전역 직렬화로 바뀌어 처리량만 무너지고 원인은 로그 한 줄로 묻힌다.
     * 잘못 건 락으로 도는 것보다 즉시 실패하는 편이 낫다.
     */
    private fun parseSpELKeys(method: Method, args: Array<Any?>, keys: Array<String>): List<String> {
        if (keys.isEmpty()) return emptyList()

        val context = StandardEvaluationContext()
        paramDiscoverer.getParameterNames(method)?.forEachIndexed { i, name -> context.setVariable(name, args[i]) }

        return keys.map { key ->
            runCatching { parser.parseExpression(key).getValue(context, String::class.java) }
                .getOrElse { e ->
                    throw IllegalStateException("Failed to evaluate @DistributedLock SpEL key '$key' on ${method.declaringClass.simpleName}.${method.name}", e)
                }
                ?: throw IllegalStateException("@DistributedLock SpEL key '$key' evaluated to null on ${method.declaringClass.simpleName}.${method.name}")
        }
    }

    /** @LockKey 어노테이션이 붙은 파라미터 값 추출 (이름 순 정렬) */
    private fun extractAnnotatedKeys(method: Method, args: Array<Any?>, parameterNames: Array<String>?): List<String> {
        val names = parameterNames ?: Array(args.size) { "arg$it" }

        return args.indices
            .filter { i -> method.parameterAnnotations[i].any { it is LockKey } }
            .map { i -> names[i] to (args[i]?.toString() ?: "null") }
            .sortedBy { it.first }
            .map { it.second }
    }
}
