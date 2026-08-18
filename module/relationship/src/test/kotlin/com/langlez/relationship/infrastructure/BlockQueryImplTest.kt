package com.langlez.relationship.infrastructure

import com.langlez.relationship.infrastructure.jpa.BlockJpaRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk

class BlockQueryImplTest : BehaviorSpec({

    val jpa = mockk<BlockJpaRepository>()
    val query = BlockQueryImpl(jpa)

    afterEach { clearMocks(jpa, answers = false) }

    /** 차단 행은 단방향으로만 저장된다. 그 한 방향만 true 로 두고 나머지는 전부 false 로 스텁한다. */
    fun blocked(blockerId: Long, blockedId: Long) {
        every { jpa.existsByBlockerIdAndBlockedId(any(), any()) } returns false
        every { jpa.existsByBlockerIdAndBlockedId(blockerId, blockedId) } returns true
    }

    Given("1번이 2번을 차단한 상태에서") {

        When("차단한 쪽(1번) 기준으로 조회하면") {
            blocked(blockerId = 1L, blockedId = 2L)

            Then("true 를 반환한다") {
                query.isBlockedBetween(1L, 2L) shouldBe true
            }
        }

        When("차단당한 쪽(2번) 기준으로 조회하면") {
            blocked(blockerId = 1L, blockedId = 2L)

            Then("역방향 조회도 true 를 반환한다") {
                query.isBlockedBetween(2L, 1L) shouldBe true
            }
        }

        When("차단과 무관한 쌍을 조회하면") {
            blocked(blockerId = 1L, blockedId = 2L)

            Then("false 를 반환한다") {
                query.isBlockedBetween(3L, 4L) shouldBe false
            }
        }
    }
})
