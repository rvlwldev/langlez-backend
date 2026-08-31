package com.langlez.relationship.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.relationship.contract.MemberFollowedEvent
import com.langlez.relationship.infrastructure.jpa.RelationshipOutBoxRepository
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBox
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

class RelationshipEventListenerTest : BehaviorSpec({

    val repo = mockk<RelationshipOutBoxRepository>()
    val listener = RelationshipEventListener(repo, ObjectMapper())

    afterEach { clearMocks(repo, answers = false) }

    Given("팔로우 이벤트가 발행되면") {

        When("리스너가 받으면") {
            Then("member-followed 아웃박스 행이 남는다") {
                val saved = slot<RelationshipOutBox>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                listener.onMemberFollowed(MemberFollowedEvent(9L, 1L, 2L))

                saved.captured.topic shouldBe "member-followed"
                saved.captured.domain shouldBe "RELATIONSHIP"
                // 같은 대상에 대한 이벤트 순서를 지키려면 키가 팔로우 대상이어야 한다.
                saved.captured.key shouldBe "2"
                saved.captured.payload!! shouldContain "\"followerId\":1"
                // 컨슈머 멱등 키가 여기서 나온다. 페이로드에 안 실리면 재팔로우가 중복으로 걸린다.
                saved.captured.payload!! shouldContain "\"followId\":9"
            }
        }
    }
})
