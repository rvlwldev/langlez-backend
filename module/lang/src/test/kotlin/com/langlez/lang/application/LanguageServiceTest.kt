package com.langlez.lang.application

import com.langlez.exception.LanglezException
import com.langlez.lang.api.request.LangReplaceLanguagesRequest
import com.langlez.lang.api.request.LangReplaceLanguagesRequest.Item
import com.langlez.lang.domain.MemberLanguage
import com.langlez.lang.domain.MemberLanguage.Level
import com.langlez.lang.domain.MemberLanguage.Role
import com.langlez.lang.domain.MemberLanguageRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class LanguageServiceTest : BehaviorSpec({

    val repo = mockk<MemberLanguageRepository>(relaxed = true)
    val service = LanguageService(repo)

    afterEach { clearMocks(repo, answers = false) }

    fun native(language: String) = Item(language = language, role = Role.NATIVE)
    fun learning(language: String, level: Level = Level.BEGINNER) =
        Item(language = language, role = Role.LEARNING, level = level)

    fun request(vararg items: Item) = LangReplaceLanguagesRequest(items.toList())

    Given("언어 프로필 전체 교체 시") {

        When("모국어 하나와 학습언어 하나를 보내면") {
            every { repo.saveAll(any()) } answers { firstArg<Collection<MemberLanguage>>().toList() }

            val saved = service.replace(1L, request(native("ko"), learning("en", Level.INTERMEDIATE)))

            Then("기존 행을 지우고 새로 저장한다") {
                verify(exactly = 1) { repo.deleteAll(1L) }
                saved shouldHaveSize 2
            }
        }

        When("같은 언어를 모국어와 학습언어로 동시에 보내면") {
            Then("400 으로 거부하고 저장을 시도조차 하지 않는다") {
                val e = shouldThrow<LanglezException> {
                    service.replace(1L, request(native("ko"), learning("ko")))
                }
                e.status.value() shouldBe 400
                e.message shouldBe "lang.duplicated"
                verify(exactly = 0) { repo.deleteAll(any()) }
                verify(exactly = 0) { repo.saveAll(any()) }
            }
        }

        When("같은 언어를 두 번 보내면") {
            Then("400 으로 거부한다") {
                val e = shouldThrow<LanglezException> {
                    service.replace(1L, request(learning("en"), learning("en", Level.ADVANCED)))
                }
                e.message shouldBe "lang.duplicated"
            }
        }

        When("모국어를 네 개 보내면") {
            Then("상한(3) 초과로 400 이다") {
                val e = shouldThrow<LanglezException> {
                    service.replace(1L, request(native("ko"), native("ja"), native("en"), native("de")))
                }
                e.status.value() shouldBe 400
                e.message shouldBe "lang.native.limit-exceeded"
            }
        }

        When("학습언어를 여섯 개 보내면") {
            Then("상한(5) 초과로 400 이다") {
                val e = shouldThrow<LanglezException> {
                    service.replace(
                        1L,
                        request(
                            learning("ko"), learning("ja"), learning("en"),
                            learning("de"), learning("fr"), learning("es"),
                        ),
                    )
                }
                e.message shouldBe "lang.learning.limit-exceeded"
            }
        }

        When("모국어에 레벨을 실어 보내면") {
            Then("도메인 불변식 위반이 500 이 아니라 400 으로 나간다") {
                val e = shouldThrow<LanglezException> {
                    service.replace(1L, request(Item("ko", Role.NATIVE, Level.BEGINNER)))
                }
                e.status.value() shouldBe 400
                e.message shouldBe "lang.level.invalid"
            }
        }

        When("지원하지 않는 언어를 보내면") {
            Then("400 이다") {
                shouldThrow<LanglezException> {
                    service.replace(1L, request(native("kr")))
                }.message shouldBe "lang.unsupported"
            }
        }

        When("빈 목록을 보내면") {
            every { repo.saveAll(any()) } returns emptyList()

            Then("전체 삭제로 취급한다 — 전체 교체 시맨틱이다") {
                service.replace(1L, request()) shouldBe emptyList()
                verify(exactly = 1) { repo.deleteAll(1L) }
            }
        }
    }
})
