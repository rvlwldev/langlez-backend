package com.langlez.redis.distributedLock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component

/**
 * 분산 락 처리를 위한 헬퍼 클래스
 */
@Component
class DistributedLockHelper {
    /**
     * 대상 객체에 RedisLockService를 주입
     */
    fun injectRedisLockService(
        target: Any,
        redisLockService: RedisLockService,
    ) {
        if (target is DistributedLockSupport) {
            target.initialize(redisLockService)
        }
    }

    /**
     * 메서드 파라미터에서 @LockKey 어노테이션이 붙은 값들을 추출
     */
    fun extractLockKeys(joinPoint: ProceedingJoinPoint): List<String> {
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method
        val parameterAnnotations = method.parameterAnnotations
        val args = joinPoint.args

        val keys = mutableListOf<String>()
        for (i in args.indices) {
            val annotations = parameterAnnotations[i]
            if (annotations.any { it is LockKey }) {
                keys.add(args[i]?.toString() ?: "null")
            }
        }

        // 만약 @LockKey가 하나도 없다면 모든 파라미터를 키로 사용 (폴백 로직)
        if (keys.isEmpty() && args.isNotEmpty()) {
            return args.map { it?.toString() ?: "null" }
        }

        return keys
    }

    /**
     * 접두사와 추출된 키들을 조합하여 최종 락 키 생성
     */
    fun generateLockKey(
        prefix: String,
        keys: List<String>,
    ): String = "$prefix${keys.joinToString(":")}"
}
