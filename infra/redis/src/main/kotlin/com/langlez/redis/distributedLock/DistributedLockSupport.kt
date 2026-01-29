package com.langlez.redis.distributedLock

/**
 * 도메인 서비스에서 분산락을 쉽게 사용하기 위한 추상 클래스
 *
 * 사용 예시:
 * class UserService : DistributedLockSupport() {
 *     @DistributedLock
 *     fun updateUser(
 *         @LockKey name: String,
 *         @LockKey number: Long,
 *         request: UpdateUserRequest
 *     ) {
 *         // 비즈니스 로직...
 *     }
 * }
 */
abstract class DistributedLockSupport {
    protected lateinit var redisLockService: RedisLockService
        private set

    /**
     * RedisLockService 주입을 위한 초기화 메서드
     * AOP Aspect에서 이 메서드를 통해 서비스를 주입합니다
     */
    fun initialize(redisLockService: RedisLockService) {
        this.redisLockService = redisLockService
    }
}
