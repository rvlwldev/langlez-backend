package com.langlez.profile.infrastructure

import com.langlez.profile.domain.ProfileRepository
import com.langlez.profile.infrastructure.jpa.ProfileImageJpaRepository
import com.langlez.profile.infrastructure.jpa.ProfileJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.maps.shouldBeEmpty
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

class ProfileRepositoryImplTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    // JPA와 Querydsl은 이 테스트에서 사용하지 않으므로 mockk로 목 객체 주입
    val profileJpa = mockk<ProfileJpaRepository>()
    val imageJpa = mockk<ProfileImageJpaRepository>()
    val dsl = mockk<JPAQueryFactory>()
    val members = mockk<com.langlez.member.contract.MemberQuery>()

    val repository: ProfileRepository = ProfileRepositoryImpl(
        profileJpa = profileJpa,
        imageJpa = imageJpa,
        redisson = redissonClient,
        dsl = dsl,
        members = members,
    )

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    beforeEach {
        redissonClient.keys.flushall()
    }

    // handle → 회원 id 는 core 포트가 답한다. QueryDSL 이 members 를 조인하지 않는다.
    Given("handle 로 프로필을 찾을 때") {

        When("없는 handle 이면") {
            Then("member_profiles 를 조회하지 않고 null 을 돌려준다") {
                every { members.findIdByHandle("ghost") } returns null

                repository.findProfileByUsername("ghost") shouldBe null

                verify(exactly = 0) { profileJpa.findById(any()) }
            }
        }
    }

    Given("없는 handle 의 방문수를 DB 에 반영할 때") {

        When("incrementVisitCountInDb 를 호출하면") {
            Then("조용히 넘어간다 (한 건 때문에 나머지 회원 방문수까지 롤백되면 안 된다)") {
                every { members.findIdByHandle("ghost") } returns null

                repository.incrementVisitCountInDb("ghost", 3L)

                verify(exactly = 0) { dsl.update(any()) }
            }
        }
    }

    Given("ProfileRepository의 방문자 수 Flush 기능 검증") {

        When("아무 방문 데이터가 없을 때 beginVisitCountFlush를 호출하면") {
            Then("빈 맵을 반환한다") {
                val result = repository.beginVisitCountFlush()
                result.shouldBeEmpty()
            }
        }

        When("방문 데이터를 추가하고 beginVisitCountFlush 후 commitVisitCountFlush를 호출하면") {
            Then("방문 카운트가 반환되고 commit 후에는 키가 삭제된다") {
                repository.increaseVisitCount(100L, "userA")
                repository.increaseVisitCount(101L, "userA")
                repository.increaseVisitCount(200L, "userB")

                val counts = repository.beginVisitCountFlush()

                counts.size shouldBe 2
                counts["userA"] shouldBe 2L
                counts["userB"] shouldBe 1L

                repository.commitVisitCountFlush(counts.keys)
                
                // 다시 flush 했을 때 비어있어야 함
                repository.beginVisitCountFlush().shouldBeEmpty()
            }
        }

        When("beginVisitCountFlush 도중 새로 방문이 추가되면 (PFADD)") {
            Then("1차 flush에는 기존 방문만 잡히고 2차 flush에는 새로 추가된 방문이 유실 없이 잡힌다") {
                repository.increaseVisitCount(100L, "userA")
                val firstFlush = repository.beginVisitCountFlush()
                
                // 1차 flush 이후 원래 키 이름으로 새로 방문 추가
                repository.increaseVisitCount(101L, "userA")
                repository.increaseVisitCount(102L, "userA")

                firstFlush["userA"] shouldBe 1L

                // 1차 flush 커밋 완료
                repository.commitVisitCountFlush(firstFlush.keys)

                // 2차 flush 수행
                val secondFlush = repository.beginVisitCountFlush()
                secondFlush["userA"] shouldBe 2L
                
                repository.commitVisitCountFlush(secondFlush.keys)
            }
        }

        When("commitVisitCountFlush를 호출하지 않고 다시 beginVisitCountFlush를 호출하면 (DB 커밋 실패 시나리오)") {
            Then("방문 카운트가 유실되지 않고 그대로 다시 읽힌다") {
                repository.increaseVisitCount(100L, "userA")
                repository.increaseVisitCount(101L, "userA")

                val firstFlush = repository.beginVisitCountFlush()
                
                // RENAME이 되어서 :flushing 접미사가 붙어있는 상태
                // DB 커밋 실패로 commit을 안 하고 다시 beginVisitCountFlush 호출
                val secondFlush = repository.beginVisitCountFlush()

                secondFlush["userA"] shouldBe 2L

                // 최종 commit 호출로 정상 삭제된다
                repository.commitVisitCountFlush(secondFlush.keys)
                repository.beginVisitCountFlush().shouldBeEmpty()
            }
        }
    }
})
