package com.langlez.notification.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalTime

class NotificationSettingTest : BehaviorSpec({

    // UTC 로 고정해 Instant -> LocalTime 변환이 테스트 작성 시각과 무관하게 정확히 대응하게 한다.
    fun at(hour: Int, minute: Int = 0) = Instant.parse("2026-01-01T00:00:00Z").plusSeconds((hour * 3600 + minute * 60).toLong())

    fun setting(from: LocalTime?, to: LocalTime?) =
        NotificationSetting(memberId = 1L, quietFrom = from, quietTo = to, timeZone = "UTC")

    Given("방해금지 구간이 자정을 넘지 않을 때 (09:00~18:00)") {
        val s = setting(LocalTime.of(9, 0), LocalTime.of(18, 0))

        When("구간 안이면") {
            Then("true") { s.isQuietAt(at(12)) shouldBe true }
        }
        When("구간 밖이면") {
            Then("false") { s.isQuietAt(at(20)) shouldBe false }
        }
        When("시작 경계(09:00) 정각이면") {
            Then("포함된다(true)") { s.isQuietAt(at(9, 0)) shouldBe true }
        }
        When("종료 경계(18:00) 정각이면") {
            Then("포함되지 않는다(false, 반열린 구간)") { s.isQuietAt(at(18, 0)) shouldBe false }
        }
    }

    Given("방해금지 구간이 자정을 넘을 때 (22:00~07:00)") {
        val s = setting(LocalTime.of(22, 0), LocalTime.of(7, 0))

        When("자정 이전 구간(23:00)이면") {
            Then("true") { s.isQuietAt(at(23)) shouldBe true }
        }
        When("자정 이후 구간(03:00)이면") {
            Then("true") { s.isQuietAt(at(3)) shouldBe true }
        }
        When("구간 밖(12:00)이면") {
            Then("false") { s.isQuietAt(at(12)) shouldBe false }
        }
        When("시작 경계(22:00) 정각이면") {
            Then("포함된다(true)") { s.isQuietAt(at(22, 0)) shouldBe true }
        }
        When("종료 경계(07:00) 정각이면") {
            Then("포함되지 않는다(false)") { s.isQuietAt(at(7, 0)) shouldBe false }
        }
    }

    Given("방해금지 설정이 불완전할 때") {
        When("quietFrom/quietTo 가 둘 다 없으면") {
            Then("항상 false") { setting(null, null).isQuietAt(at(23)) shouldBe false }
        }

        When("timeZone 이 없으면") {
            Then("판정하지 않는다(false, fail-open) — 서버 시간으로 대신 판정하지 않는다") {
                val s = NotificationSetting(memberId = 1L, quietFrom = LocalTime.of(22, 0), quietTo = LocalTime.of(7, 0), timeZone = null)
                s.isQuietAt(Instant.now()) shouldBe false
            }
        }

        When("timeZone 파싱이 실패하면") {
            Then("판정하지 않는다(false, fail-open)") {
                val s = NotificationSetting(
                    memberId = 1L,
                    quietFrom = LocalTime.of(22, 0),
                    quietTo = LocalTime.of(7, 0),
                    timeZone = "Not/AZone",
                )
                s.isQuietAt(Instant.now()) shouldBe false
            }
        }
    }

    Given("updateQuietHours 로 방해금지를 바꿀 때") {
        When("quietFrom 만 주고 quietTo 를 안 주면") {
            Then("IllegalArgumentException") {
                shouldThrow<IllegalArgumentException> {
                    setting(null, null).updateQuietHours(LocalTime.of(22, 0), null, "Asia/Seoul")
                }
            }
        }

        When("둘 다 null 이면") {
            Then("방해금지가 해제된다") {
                val s = setting(LocalTime.of(22, 0), LocalTime.of(7, 0))
                s.updateQuietHours(null, null, null)

                s.isQuietAt(Instant.now()) shouldBe false
            }
        }
    }
})
