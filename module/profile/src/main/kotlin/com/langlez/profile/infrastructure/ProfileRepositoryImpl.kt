package com.langlez.profile.infrastructure

import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import com.langlez.profile.domain.QProfile.Companion.profile
import com.querydsl.jpa.impl.JPAQueryFactory
import org.redisson.api.RedissonClient
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

interface ProfileJpaRepository : JpaRepository<Profile, Long>

interface ProfileImageJpaRepository : JpaRepository<ProfileImage, ProfileImage.Key> {
    fun findByIdAndRepresentTrue(id: Long): ProfileImage?
    fun findByIdAndUrl(id: Long, url: String): ProfileImage?
    fun countById(id: Long): Long
}

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

    override fun flushVisitCounts(): Map<String, Long> {
        val keys = redisson.keys.getKeysByPattern("$HLL_PREFIX*").toList()
        if (keys.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Long>()
        for (key in keys) {
            val hll = redisson.getHyperLogLog<Long>(key)
            val count = hll.count()
            if (count > 0) {
                result[key.removePrefix(HLL_PREFIX)] = count
                hll.delete()
            }
        }
        return result
    }

    override fun incrementVisitCountInDb(username: String, delta: Long) {
        dsl.update(profile)
            .set(profile.visitCount, profile.visitCount.add(delta))
            .where(profile.member.username.eq(username))
            .execute()
    }

    companion object {
        private const val HLL_PREFIX = "profile:visit:"
    }
}
