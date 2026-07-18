package com.langlez.profile.infrastructure

import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import com.langlez.profile.domain.QProfile.Companion.profile
import com.langlez.profile.infrastructure.jpa.ProfileImageJpaRepository
import com.langlez.profile.infrastructure.jpa.ProfileJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.redisson.api.RedissonClient
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProfileRepositoryImpl(
    private val profileJpa: ProfileJpaRepository,
    private val imageJpa: ProfileImageJpaRepository,
    private val redisson: RedissonClient,
    private val dsl: JPAQueryFactory,
) : ProfileRepository {

    override fun saveImage(image: ProfileImage): ProfileImage = imageJpa.save(image)

    override fun findRepresentImage(id: Long): ProfileImage? = imageJpa.findByIdAndRepresentTrue(id)

    override fun findImageByUrl(memberId: Long, url: String): ProfileImage? = imageJpa.findByIdAndUrl(memberId, url)

    override fun countImages(id: Long): Long = imageJpa.countById(id)

    @Cacheable(cacheNames = ["profile"], key = "#id")
    override fun findProfile(id: Long): Profile? = profileJpa.findByIdOrNull(id)

    override fun findProfileByUsername(username: String): Profile? =
        dsl.selectFrom(profile)
            .where(profile.member.username.eq(username))
            .fetchOne()

    @CacheEvict(cacheNames = ["profile"], key = "#profile.id")
    override fun saveProfile(profile: Profile): Profile = profileJpa.save(profile)

    override fun increaseVisitCount(visitorId: Long, username: String) {
        redisson.getHyperLogLog<Long>("$HLL_PREFIX$username").add(visitorId)
    }

    override fun getVisitCountDelta(username: String): Long =
        redisson.getHyperLogLog<Long>("$HLL_PREFIX$username").count()

    override fun beginVisitCountFlush(): Map<String, Long> {
        val keys = redisson.keys.getKeysByPattern("$HLL_PREFIX*").toList()
        if (keys.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Long>()
        for (key in keys) {
            val flushingKey = if (key.endsWith(FLUSHING_SUFFIX)) key else "$key$FLUSHING_SUFFIX"
            if (flushingKey != key) {
                try {
                    // 원자적 RENAME: 새 PFADD는 원래 키 이름으로 다시 생성되므로 유실되지 않는다
                    redisson.getBucket<Any>(key).rename(flushingKey)
                } catch (e: Exception) {
                    // RENAME 대상 키가 존재하지 않는 경우(레이스 컨디션 등) 예외 없이 건너뛴다
                }
            }
            val count = redisson.getHyperLogLog<Long>(flushingKey).count()
            if (count > 0) {
                val username = flushingKey.removePrefix(HLL_PREFIX).removeSuffix(FLUSHING_SUFFIX)
                result[username] = count
            }
        }
        return result
    }

    override fun commitVisitCountFlush(usernames: Collection<String>) {
        val flushingKeys = usernames.map { "$HLL_PREFIX$it$FLUSHING_SUFFIX" }
        if (flushingKeys.isNotEmpty()) {
            redisson.keys.delete(*flushingKeys.toTypedArray())
        }
    }

    override fun incrementVisitCountInDb(username: String, delta: Long) {
        dsl.update(profile)
            .set(profile.visitCount, profile.visitCount.add(delta))
            .where(profile.member.username.eq(username))
            .execute()
    }

    companion object {
        private const val HLL_PREFIX = "profile:visit:"
        private const val FLUSHING_SUFFIX = ":flushing"
    }
}
