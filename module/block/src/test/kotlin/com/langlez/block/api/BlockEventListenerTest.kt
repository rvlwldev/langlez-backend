package com.langlez.block.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.block.contract.MemberBlockedEvent
import com.langlez.block.infrastructure.jpa.BlockOutBoxRepository
import com.langlez.block.infrastructure.outbox.BlockOutBox
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

class BlockEventListenerTest : BehaviorSpec({

    val repo = mockk<BlockOutBoxRepository>()
    val listener = BlockEventListener(repo, ObjectMapper())

    afterEach { clearMocks(repo, answers = false) }

    Given("차단 이벤트가 발행되면") {

        When("리스너가 받으면") {
            Then("member-blocked 아웃박스 행이 남는다") {
                val saved = slot<BlockOutBox>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                listener.onMemberBlocked(MemberBlockedEvent(1L, 2L, 1_700_000_000_000L))

                saved.captured.topic shouldBe "member-blocked"
                saved.captured.domain shouldBe "BLOCK"
                // 같은 사람에 대한 이벤트 순서를 지키려면 키가 차단당한 쪽이어야 한다.
                saved.captured.key shouldBe "2"
                saved.captured.payload!! shouldContain "\"blockerId\":1"
                // 컨슈머 멱등 키가 여기서 나온다. 페이로드에 안 실리면 재차단 수습이 중복으로 걸린다.
                saved.captured.payload!! shouldContain "\"occurredAt\":1700000000000"
            }
        }
    }
})
