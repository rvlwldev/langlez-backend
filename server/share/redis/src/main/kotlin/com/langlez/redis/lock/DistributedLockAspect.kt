package com.langlez.redis.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.annotation.Order
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

@Aspect
@Order(1)
class DistributedLockAspect(
    private val lockService: RedisLockService,
    private val transactionManager: ObjectProvider<PlatformTransactionManager>,
) {

    private val parameterNames = DefaultParameterNameDiscoverer()
    private val parser = SpelExpressionParser()

    @Around("@annotation(distributedLock)")
    fun lock(point: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
        val signature = point.signature as MethodSignature
        val method = signature.method
        val args = point.args

        val keys = buildList {
            addAll(evaluateKeys(method, args, distributedLock.keys))
            addAll(annotatedKeys(method, args, signature.parameterNames))
            if (isEmpty()) add("${method.declaringClass.name}:${method.name}")
        }

        return lockService.executeWithLock(
            key = "${distributedLock.prefix}${keys.joinToString(":")}",
            waitTime = distributedLock.retries * distributedLock.waitMs,
            leaseTime = distributedLock.leaseSecs * 1000,
            unit = TimeUnit.MILLISECONDS,
            throwOnFailure = distributedLock.throwOnFailure,
        ) {
            if (distributedLock.transactional) {
                TransactionTemplate(transactionManager.getObject()).execute { point.proceed() }
            } else {
                point.proceed()
            }
        }
    }

    private fun evaluateKeys(method: Method, args: Array<Any?>, expressions: Array<String>): List<String> {
        if (expressions.isEmpty()) return emptyList()

        val context = StandardEvaluationContext()
        parameterNames.getParameterNames(method)?.forEachIndexed { i, name -> context.setVariable(name, args[i]) }

        return expressions.map { expression ->
            val value = runCatching { parser.parseExpression(expression).getValue(context, String::class.java) }
                .getOrElse {
                    throw IllegalStateException("@DistributedLock 키를 계산하지 못했다: '$expression' (${method.declaringClass.simpleName}.${method.name})", it)
                }
            value ?: throw IllegalStateException("@DistributedLock 키가 null 이다: '$expression' (${method.declaringClass.simpleName}.${method.name})")
        }
    }

    private fun annotatedKeys(method: Method, args: Array<Any?>, names: Array<String>?): List<String> {
        val resolved = names ?: Array(args.size) { "arg$it" }

        return args.indices
            .filter { i -> method.parameterAnnotations[i].any { it is LockKey } }
            .map { i -> resolved[i] to (args[i]?.toString() ?: "null") }
            .sortedBy { it.first }
            .map { it.second }
    }
}
