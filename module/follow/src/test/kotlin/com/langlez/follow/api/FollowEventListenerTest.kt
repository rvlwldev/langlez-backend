package com.langlez.follow.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.follow.contract.MemberFollowedEvent
import com.langlez.follow.infrastructure.jpa.FollowOutBoxRepository
import com.langlez.follow.infrastructure.outbox.FollowOutBox
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

class FollowEventListenerTest : BehaviorSpec({

    val repo = mockk<FollowOutBoxRepository>()
    val listener = FollowEventListener(repo, ObjectMapper())

    afterEach { clearMocks(repo, answers = false) }

    Given("팔로우 이벤트가 발행되면") {

        When("리스너가 받으면") {
            Then("member-followed 아웃박스 행이 남는다") {
                val saved = slot<FollowOutBox>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                listener.onMemberFollowed(MemberFollowedEvent(9L, 1L, 2L))

                saved.captured.topic shouldBe "member-followed"
                saved.captured.domain shouldBe "FOLLOW"
                // 같은 대상에 대한 이벤트 순서를 지키려면 키가 팔로우 대상이어야 한다.
                saved.captured.key shouldBe "2"
                saved.captured.payload!! shouldContain "\"followerId\":1"
                // 컨슈머 멱등 키가 여기서 나온다. 페이로드에 안 실리면 재팔로우가 중복으로 걸린다.
                saved.captured.payload!! shouldContain "\"followId\":9"
            }
        }
    }
})
