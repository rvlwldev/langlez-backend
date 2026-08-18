package com.langlez.member.api

import com.langlez.core.OnlineTracker
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify

class MemberPingControllerTest : BehaviorSpec({

    val tracker = mockk<OnlineTracker>(relaxed = true)
    val controller = MemberPingController(tracker)

    Given("앱이 살아 있다는 핑이 오면") {
        Then("접속 상태만 갱신한다") {
            controller.ping(1L)

            verify(exactly = 1) { tracker.toOnline(1L) }
        }
    }
})
