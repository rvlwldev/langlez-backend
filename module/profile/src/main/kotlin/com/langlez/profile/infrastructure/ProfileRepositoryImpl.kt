package com.langlez.profile.infrastructure

import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface ProfileJpaRepository : JpaRepository<Profile, Long> {

    @Modifying
    @Query("UPDATE Profile p SET p.visitCount = p.visitCount + :delta WHERE p.id = (SELECT m.id FROM Member m WHERE m.username = :username)")
    fun incrementVisitCount(username: String, delta: Long): Int
}

interface ProfileImageJpaRepository : JpaRepository<ProfileImage, ProfileImage.Key> {
    fun findByIdAndRepresentTrue(id: Long): ProfileImage?
    fun countById(id: Long): Long
}

@Repository
class ProfileRepositoryImpl(
    private val profileJpa: ProfileJpaRepository,
    private val imageJpa: ProfileImageJpaRepository,
    private val redis: RedisTemplate<String, Any>,
) : ProfileRepository {

    override fun saveImage(image: ProfileImage): ProfileImage = imageJpa.save(image)

    override fun findRepresentImage(id: Long): ProfileImage? = imageJpa.findByIdAndRepresentTrue(id)

    override fun countImages(id: Long): Long = imageJpa.countById(id)

    @Cacheable(cacheNames = ["profile"], key = "#id")
    override fun findProfile(id: Long): Profile? = profileJpa.findByIdOrNull(id)

    @CacheEvict(cacheNames = ["profile"], key = "#profile.id")
    override fun saveProfile(profile: Profile): Profile = profileJpa.save(profile)

    override fun increaseVisitCount(visitorId: Long, username: String) {
        val hllKey = "$HLL_PREFIX$username"
        redis.opsForHyperLogLog().add(hllKey, visitorId)
    }

    @Transactional
    override fun incrementVisitCount(username: String, delta: Long) {
        profileJpa.incrementVisitCount(username, delta)
    }

    override fun getVisitCountDelta(username: String): Long {
        val hllKey = "$HLL_PREFIX$username"
        return redis.opsForHyperLogLog().size(hllKey)
    }

    override fun flushVisitCounts(): Map<String, Long> {
        val keys = redis.keys("$HLL_PREFIX*") ?: return emptyMap()
        val result = mutableMapOf<String, Long>()

        for (key in keys) {
            val username = key.removePrefix(HLL_PREFIX)
            val count = redis.opsForHyperLogLog().size(key)
            if (count > 0) {
                result[username] = count
                redis.delete(key)
            }
        }

        return result
    }

    companion object {
        private const val HLL_PREFIX = "profile:visit:"
    }
}
