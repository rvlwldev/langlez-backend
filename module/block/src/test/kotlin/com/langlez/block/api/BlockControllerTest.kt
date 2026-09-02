package com.langlez.block.api

import com.langlez.block.application.BlockMemberView
import com.langlez.block.application.BlockService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class BlockControllerTest : BehaviorSpec({

    val service = mockk<BlockService>()
    val controller = BlockController(service)

    afterEach { clearMocks(service, answers = false) }

    fun view(memberId: Long) = BlockMemberView(memberId, memberId, "user$memberId", null)

    Given("차단 목록 요청 시") {

        When("size 를 터무니없이 크게 보내면") {
            Then("상한 50 으로 깎여서 서비스로 간다") {
                every { service.listBlocks(1L, 50, null) } returns emptyList()

                controller.listBlocks(memberId = 1L, size = 1_000_000, cursor = null)

                verify { service.listBlocks(1L, 50, null) }
            }
        }

        When("size 를 0 이하로 보내면") {
            Then("최소 1 로 올라간다") {
                every { service.listBlocks(1L, 1, null) } returns emptyList()

                controller.listBlocks(memberId = 1L, size = 0, cursor = null)

                verify { service.listBlocks(1L, 1, null) }
            }
        }

        When("커서를 함께 보내면") {
            Then("그대로 서비스에 전달한다") {
                every { service.listBlocks(1L, 20, 30L) } returns listOf(view(3L))

                controller.listBlocks(memberId = 1L, size = 20, cursor = 30L).map { it.memberId } shouldBe listOf(3L)

                verify { service.listBlocks(1L, 20, 30L) }
            }
        }
    }

    // 차단 대상은 경로 변수고 차단하는 쪽은 @MemberId 다. 둘을 바꿔 넘기면 반대로 차단된다 —
    // 컴파일도 되고 조용히 통과하는 종류라 고정한다.
    Given("차단 요청 시") {

        When("요청자와 대상이 다르면") {
            Then("요청자가 차단하는 쪽이다") {
                every { service.block(7L, 99L) } returns Unit

                controller.block(memberId = 7L, targetId = 99L)

                verify { service.block(7L, 99L) }
            }
        }
    }
})
