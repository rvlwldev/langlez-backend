package com.langlez.member.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * 정지는 되돌릴 수 있고, 탈퇴는 되돌릴 수 없다.
 * 탈퇴해도 계정 정보는 지우지 않는다 — 재가입 후 다시 문제를 일으키는 회원을 추적해야 하기 때문이다.
 */
class MemberStatusTest : BehaviorSpec({

    fun member() = Member(
        email = "u@test.com",
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
        status = Member.Status.ACTIVE,
    )

    Given("회원을 정지하면") {
        val target = member().apply { suspend() }

        Then("상태가 SUSPENDED 가 되고 정지 시각이 남는다") {
            target.status shouldBe Member.Status.SUSPENDED
            target.audit.suspendedAt.shouldNotBeNull()
        }

        Then("서비스 이용이 막힌다") {
            shouldThrow<IllegalArgumentException> { target.requireActive() }
        }
    }

    Given("정지된 회원을 어드민이 해제하면") {
        val target = member().apply { suspend() }
        target.unsuspend()

        Then("상태가 ACTIVE 로 돌아오고 정지 시각이 지워진다") {
            target.status shouldBe Member.Status.ACTIVE
            target.audit.suspendedAt shouldBe null
        }

        Then("다시 서비스를 쓸 수 있다") {
            target.requireActive()
        }
    }

    Given("회원이 탈퇴하면") {
        val target = member().apply { withdraw() }

        Then("상태가 WITHDRAWN 이 되고 탈퇴 시각이 남는다") {
            target.status shouldBe Member.Status.WITHDRAWN
            target.audit.withdrawnAt.shouldNotBeNull()
        }

        Then("개인정보는 지워지지 않고 그대로 보존된다") {
            // 재가입 후 재발 추적용. 익명화/삭제 배치를 두지 않는다.
            target.email shouldBe "u@test.com"
            target.providerId shouldBe "p1"
        }
    }

    Given("탈퇴한 회원은") {
        val target = member().apply { withdraw() }

        Then("정지시킬 수 없다") {
            shouldThrow<IllegalArgumentException> { target.suspend() }
        }

        Then("정지 해제로 되살릴 수 없다") {
            shouldThrow<IllegalArgumentException> { target.unsuspend() }
        }
    }

    Given("가입 직후 CREATED 상태에서 인증을 마치면") {
        val target = Member(email = "n@test.com", provider = Member.Provider.GOOGLE, providerId = "p2")

        Then("ACTIVE 로 전이한다") {
            target.status shouldBe Member.Status.CREATED
            target.verify()
            target.status shouldBe Member.Status.ACTIVE
        }
    }
})
