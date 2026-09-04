package com.langlez.member.infrastructure

import com.langlez.member.contract.MemberReader
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.util.Locale

/**
 * 다른 모듈이 회원 정보를 보는 유일한 통로다. 계정 정보·상태·푸시 토큰을 한 어댑터가 다 낸다.
 * 여기가 깨지면 프로필 화면의 성별/국가/생년월일이 통째로 사라지고, 인증 필터의 상태 검사도 함께 무너진다.
 */
class MemberReaderImplTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val query = MemberReaderImpl(repo)

    afterEach { clearMocks(repo, answers = false) }

    fun member(id: Long = 1L, handle: String = "user$id", fcm: String? = null) = Member(
        id = id,
        email = "user$id@test.com",
        handle = handle,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        fcm = fcm,
    )

    Given("handle 로 회원 id 를 물으면") {

        When("있는 handle 이면") {
            every { repo.find("alice") } returns member(7L, "alice")

            Then("회원 id 를 돌려준다") {
                query.findIdByHandle("alice") shouldBe 7L
            }
        }

        When("없는 handle 이면") {
            every { repo.find("ghost") } returns null

            Then("null 을 돌려준다") {
                query.findIdByHandle("ghost") shouldBe null
            }
        }
    }

    Given("프로필용 계정 정보를 물으면") {

        When("개인정보가 채워진 회원이면") {
            every { repo.find(1L) } returns member().apply {
                gender = Member.Gender.FEMALE
                birthDay = LocalDate.of(1995, 3, 14)
                country = "KR"
            }

            val info = query.findProfileInfo(1L)

            Then("성별은 enum 이 아니라 이름 문자열로 나온다") {
                info?.gender shouldBe "FEMALE"
            }

            Then("country 는 locale 로 변환돼 나온다") {
                info?.locale shouldBe Locale.of("", "KR")
            }

            Then("생년월일과 handle 이 함께 실린다") {
                info?.birthDay shouldBe LocalDate.of(1995, 3, 14)
                info?.handle shouldBe "user1"
            }
        }

        // 목록에서 정지·탈퇴 회원을 걸러내는 유일한 근거가 이 필드다(matching 이 쓴다).
        // Member.Status -> MemberReader.Status 가 when 매핑이라 분기마다 확인한다.
        When("가입 인증을 마친 회원이면") {
            every { repo.find(1L) } returns member().apply { verify() }

            Then("status 가 ACTIVE 로 실린다") {
                query.findProfileInfo(1L)?.status shouldBe MemberReader.Status.ACTIVE
            }
        }

        When("아직 인증 전인 회원이면") {
            every { repo.find(1L) } returns member()

            Then("status 가 CREATED 로 실린다") {
                query.findProfileInfo(1L)?.status shouldBe MemberReader.Status.CREATED
            }
        }

        When("정지된 회원이면") {
            every { repo.find(1L) } returns member().apply { suspend() }

            Then("status 가 SUSPENDED 로 실린다") {
                query.findProfileInfo(1L)?.status shouldBe MemberReader.Status.SUSPENDED
            }
        }

        // 탈퇴해도 members 행을 지우지 않는 정책이라 null 이 아니라 WITHDRAWN 으로 온다.
        // 소비자가 null 검사만으로 거르지 못하는 이유가 이것이다.
        When("탈퇴한 회원이면") {
            every { repo.find(1L) } returns member().apply { withdraw() }

            Then("null 이 아니라 status = WITHDRAWN 으로 실린다") {
                val withdrawn = query.findProfileInfo(1L)
                withdrawn shouldNotBe null
                withdrawn?.status shouldBe MemberReader.Status.WITHDRAWN
            }
        }

        When("닉네임을 정하지 않은 회원이면") {
            every { repo.find(1L) } returns member()

            Then("nickname 은 null 로 나온다") {
                query.findProfileInfo(1L)?.nickname shouldBe null
            }
        }

        When("닉네임을 정한 회원이면") {
            every { repo.find(1L) } returns member().apply { changeNickname("지수") }

            Then("nickname 이 그대로 나온다") {
                query.findProfileInfo(1L)?.nickname shouldBe "지수"
            }
        }

        When("없는 회원이면") {
            every { repo.find(99L) } returns null

            Then("null 을 돌려준다") {
                query.findProfileInfo(99L) shouldBe null
            }
        }
    }

    Given("여러 회원의 계정 정보를 한 번에 물으면") {

        // 목록 화면에서 회원 수만큼 단건 조회가 나가면 N+1 이다. 왕복 한 번으로 끝나야 한다.
        When("id 가 중복돼 들어오면") {
            every { repo.findAll(setOf(1L, 2L)) } returns listOf(member(1L), member(2L))

            val infos = query.findProfileInfos(listOf(1L, 2L, 1L))

            Then("중복을 걷어내고 한 번만 조회한다") {
                infos shouldHaveSize 2
                infos[1L]?.handle shouldBe "user1"
                verify(exactly = 1) { repo.findAll(setOf(1L, 2L)) }
            }
        }

        When("빈 컬렉션이면") {
            Then("빈 IN () 쿼리를 만들지 않고 바로 빈 맵을 돌려준다") {
                query.findProfileInfos(emptyList()).shouldBeEmpty()
                verify(exactly = 0) { repo.findAll(any<Collection<Long>>()) }
            }
        }
    }

    Given("계정 상태를 물으면") {

        // 원본 Member.Status 에 값이 늘면 구현체의 when 이 컴파일에서 깨진다. 여기서는 매핑만 고정한다.
        When("정지된 회원이면") {
            every { repo.find(1L) } returns member().apply { suspend() }

            Then("SUSPENDED 로 매핑돼 나온다") {
                query.findStatus(1L) shouldBe MemberReader.Status.SUSPENDED
            }
        }

        When("없는 회원이면") {
            every { repo.find(99L) } returns null

            Then("null 을 돌려준다") {
                query.findStatus(99L) shouldBe null
            }
        }
    }

    Given("여러 회원의 푸시 토큰을 한 번에 물으면") {

        When("일부만 토큰을 갖고 있으면") {
            every { repo.findAll(listOf(1L, 2L, 3L)) } returns listOf(
                member(1L, fcm = "token-1"),
                member(2L, fcm = null),
                member(3L, fcm = ""),
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
