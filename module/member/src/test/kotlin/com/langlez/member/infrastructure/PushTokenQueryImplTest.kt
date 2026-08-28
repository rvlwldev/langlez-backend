package com.langlez.member.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PushTokenQueryImplTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val query = PushTokenQueryImpl(repo)

    afterEach { clearMocks(repo, answers = false) }

    fun member(id: Long, fcm: String? = null) = Member(
        id = id,
        email = "user$id@test.com",
        handle = "user$id",
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        fcm = fcm,
    )

    Given("여러 회원의 푸시 토큰을 한 번에 물으면") {

        When("일부만 토큰을 갖고 있으면") {
            every { repo.findAll(listOf(1L, 2L, 3L)) } returns listOf(
                member(1L, "token-1"),
                member(2L, null),
                member(3L, ""),
            )

            Then("토큰이 없거나 빈 문자열인 회원은 맵에서 빠진다") {
                val tokens = query.findPushTokens(listOf(1L, 2L, 3L))

                tokens shouldBe mapOf(1L to "token-1")
                verify(exactly = 1) { repo.findAll(listOf(1L, 2L, 3L)) }
            }
        }

        When("빈 컬렉션이면") {
            Then("빈 맵을 돌려준다 (빈 IN () 방지는 MemberRepository.findAll 이 이미 한다)") {
                every { repo.findAll(emptyList()) } returns emptyList()

                query.findPushTokens(emptyList()).shouldBeEmpty()
            }
        }
    }
})
