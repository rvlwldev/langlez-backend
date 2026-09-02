package com.langlez.profile.infrastructure

import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import com.langlez.profile.domain.QProfile.Companion.profile
import com.langlez.profile.infrastructure.jpa.ProfileImageJpaRepository
import com.langlez.profile.infrastructure.jpa.ProfileJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.redisson.api.RedissonClient
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

    override fun findRepresentImage(id: Long): ProfileImage? = imageJpa.findByIdAndRepresentTrueAndDeletedAtIsNull(id)

    override fun findImageByUrl(memberId: Long, url: String): ProfileImage? = imageJpa.findByIdAndUrlAndDeletedAtIsNull(memberId, url)

    override fun countImages(id: Long): Long = imageJpa.countByIdAndDeletedAtIsNull(id)

    /**
     * 캐시하지 않는다. 캐시에서 꺼낸 엔티티는 detached 라 오래된 visitCount/@Version 을 되써서
     * `VisitCountSyncScheduler` 가 DB 에 더해 둔 방문수를 덮어쓴다. PK 조회라 캐시 이득도 작다.
     */
    override fun findProfile(id: Long): Profile? = profileJpa.findByIdOrNull(id)

    override fun findProfiles(ids: List<Long>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        return profileJpa.findAllById(ids)
    }

    override fun findAllProfiles(): List<Profile> = profileJpa.findAll()

    override fun saveProfile(profile: Profile): Profile = profileJpa.save(profile)

    override fun increaseVisitCount(visitorId: Long, username: String) {
        redisson.getHyperLogLog<Long>("$HLL_PREFIX$username").add(visitorId)
        redisson.getSet<String>(DIRTY_USERNAMES_KEY).add(username)
    }

    override fun getVisitCountDelta(username: String): Long =
        redisson.getHyperLogLog<Long>("$HLL_PREFIX$username").count()

    override fun beginVisitCountFlush(): Map<String, Long> {
        val usernames = redisson.getSet<String>(DIRTY_USERNAMES_KEY).readAll()
        if (usernames.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Long>()
        for (username in usernames) {
            val key = "$HLL_PREFIX$username"
            val flushingKey = "$key$FLUSHING_SUFFIX"
            if (redisson.getBucket<Any>(key).isExists) {
                if (redisson.getBucket<Any>(flushingKey).isExists) {
                    redisson.getHyperLogLog<Long>(flushingKey).mergeWith(key)
                    redisson.getBucket<Any>(key).delete()
                } else {
                    try {
                        // 원자적 RENAME: 새 PFADD는 원래 키 이름으로 다시 생성되므로 유실되지 않는다
                        redisson.getBucket<Any>(key).rename(flushingKey)
                    } catch (e: Exception) {
                        // RENAME 대상 키가 존재하지 않는 경우 건너띤다
                    }
                }
            }
            val count = redisson.getHyperLogLog<Long>(flushingKey).count()
            if (count > 0) {
                result[username] = count
            }
        }
        return result
    }

    override fun commitVisitCountFlush(usernames: Collection<String>) {
        if (usernames.isEmpty()) return
        val flushingKeys = usernames.map { "$HLL_PREFIX$it$FLUSHING_SUFFIX" }
        redisson.keys.delete(*flushingKeys.toTypedArray())
        val dirtySet = redisson.getSet<String>(DIRTY_USERNAMES_KEY)
        for (username in usernames) {
            val key = "$HLL_PREFIX$username"
            if (!redisson.getBucket<Any>(key).isExists) {
                dirtySet.remove(username)
            }
        }
    }

    override fun incrementVisitCountInDb(memberId: Long, delta: Long) {
        dsl.update(profile)
            .set(profile.visitCount, profile.visitCount.add(delta))
            .where(profile.id.eq(memberId))
            .execute()
    }

    companion object {
        private const val HLL_PREFIX = "profile:visit:"
        private const val FLUSHING_SUFFIX = ":flushing"
        private const val DIRTY_USERNAMES_KEY = "profile:visit:dirty_usernames"
    }
}
