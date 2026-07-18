package com.langlez.echo.infrastructure

import com.langlez.echo.domain.HashtagDailyCount
import com.langlez.echo.domain.HashtagTrendCount
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import java.time.LocalDate

class HashtagTrendRepositoryImplTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val repository = HashtagTrendRepositoryImpl(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("HashtagTrendRepository 가 주어졌을 때") {

        When("포스트 작성에서 해시태그 사용을 기록하면") {
            repository.recordPostUsage("kotlin")
            repository.recordPostUsage("kotlin")
            repository.recordPostUsage("spring")

            Then("인기 해시태그 목록에 정상적으로 누적되어 반영되어야 한다") {
                val trending = repository.getTrending(1, 10)
                trending.size shouldBe 2
                trending[0] shouldBe HashtagTrendCount("kotlin", 2)
                trending[1] shouldBe HashtagTrendCount("spring", 1)
            }

            Then("해당 키에 TTL(유효기간)이 걸려있어야 한다") {
                val todayStr = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                val key = "echo:hashtag:post:$todayStr"
                val ttl = redissonClient.getScoredSortedSet<String>(key).remainTimeToLive()
                ttl shouldNotBe -1 // -1 이면 무제한(TTL 없음)
            }
        }

        When("해시태그 검색을 기록하면") {
            repository.recordSearch("kotlin")
            repository.recordSearch("java")

            Then("해당 검색 키에도 TTL이 걸려있어야 한다") {
                val todayStr = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                val key = "echo:hashtag:search:$todayStr"
                val ttl = redissonClient.getScoredSortedSet<String>(key).remainTimeToLive()
                ttl shouldNotBe -1
            }
        }

        When("하루 통계 스냅샷을 가져오면") {
            val today = LocalDate.now()
            val snapshot = repository.snapshotDailyCounts(today)

            Then("포스트 카운트와 검색 카운트가 정상적으로 합산되어 반환되어야 한다") {
                val kotlinStat = snapshot.find { it.hashtag == "kotlin" }
                kotlinStat shouldNotBe null
                kotlinStat!!.postCount shouldBe 2
                kotlinStat.searchCount shouldBe 1

                val javaStat = snapshot.find { it.hashtag == "java" }
                javaStat shouldNotBe null
                javaStat!!.postCount shouldBe 0
                javaStat.searchCount shouldBe 1
            }
        }
    }
})
