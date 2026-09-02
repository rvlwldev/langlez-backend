package com.langlez.notification.infrastructure

import com.langlez.notification.domain.NotificationMuteRepository
import com.langlez.notification.domain.NotificationSetting
import com.langlez.notification.domain.NotificationSettingRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk

/** 다른 모듈이 알림 수신 설정을 보는 유일한 통로다. */
class NotificationReaderImplTest : BehaviorSpec({

    val mutes = mockk<NotificationMuteRepository>()
    val settingsRepo = mockk<NotificationSettingRepository>()
    val reader = NotificationReaderImpl(mutes, settingsRepo)

    afterEach { clearMocks(mutes, settingsRepo, answers = false) }

    Given("한 회원의 mute 목록을 물으면") {
        When("끈 유형이 있으면") {
            every { mutes.find(1L) } returns setOf("CHAT_MESSAGE")

            Then("그대로 나온다") {
                reader.mutedTypesOf(1L) shouldBe setOf("CHAT_MESSAGE")
            }
        }

        When("끈 유형이 없으면") {
            every { mutes.find(1L) } returns emptySet()

            Then("빈 집합이 나온다") {
                reader.mutedTypesOf(1L) shouldBe emptySet()
            }
        }
    }

    Given("여러 회원의 mute 목록을 한 번에 물으면") {
        When("일부만 끈 유형이 있으면") {
            every { mutes.findAll(listOf(1L, 2L)) } returns mapOf(1L to setOf("CHAT_MESSAGE"))

            Then("나머지 회원은 결과에서 빠진다") {
                reader.mutedTypesOf(listOf(1L, 2L)) shouldBe mapOf(1L to setOf("CHAT_MESSAGE"))
            }
        }

        When("빈 컬렉션이면") {
            every { mutes.findAll(emptyList()) } returns emptyMap()

            Then("빈 맵을 돌려준다") {
                reader.mutedTypesOf(emptyList()).shouldBeEmpty()
            }
        }
    }

    Given("방해금지 여부를 물으면") {
        When("설정이 있고 지금이 그 구간이면") {
            val setting = mockk<NotificationSetting>()
            every { setting.isQuietAt(any()) } returns true
            every { settingsRepo.find(1L) } returns setting

            Then("true 다") {
                reader.isQuietNow(1L) shouldBe true
            }
        }

        When("설정이 있지만 구간 밖이면") {
            val setting = mockk<NotificationSetting>()
            every { setting.isQuietAt(any()) } returns false
            every { settingsRepo.find(1L) } returns setting

            Then("false 다") {
                reader.isQuietNow(1L) shouldBe false
            }
        }

        When("설정 자체가 없으면") {
            every { settingsRepo.find(99L) } returns null

            Then("false 다") {
                reader.isQuietNow(99L) shouldBe false
            }
        }
    }
})
