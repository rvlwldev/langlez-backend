package com.langlez.notification.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.exception.LanglezException
import com.langlez.notification.api.request.NotificationMuteUpdateRequest
import com.langlez.notification.api.request.NotificationQuietHoursUpdateRequest
import com.langlez.notification.application.NotificationService
import com.langlez.notification.application.NotificationSettingSnapshot
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationSetting
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalTime
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN

class NotificationControllerTest : BehaviorSpec({

    val service = mockk<NotificationService>()
    val controller = NotificationController(service, ObjectMapper())

    afterEach { clearMocks(service, answers = false) }

    fun notification(id: Long = 1L, recipientId: Long = 1L, data: String? = null, read: Boolean = false) =
        Notification(
            id = id,
            recipientId = recipientId,
            type = NotificationService.TYPE_CHAT_MESSAGE,
            title = NotificationService.TITLE_CHAT_MESSAGE,
            body = "안녕",
            read = read,
            data = data,
        )

    Given("알림함을 열 때") {

        When("size 를 터무니없이 크게 보내면") {
            Then("상한 50 으로 깎여서 서비스로 간다") {
                every { service.list(1L, 50, null) } returns emptyList()

                controller.list(memberId = 1L, size = 1_000_000, cursor = null)

                verify { service.list(1L, 50, null) }
            }
        }

        When("size 를 0 이하로 보내면") {
            Then("최소 1 로 올라간다") {
                every { service.list(1L, 1, null) } returns emptyList()

                controller.list(memberId = 1L, size = 0, cursor = null)

                verify { service.list(1L, 1, null) }
            }
        }

        // 목록은 본인 것만 나와야 한다. 커서나 파라미터에서 회원 id 를 받는 순간 남의 알림함이 열린다.
        When("커서를 넘기면") {
            Then("인증된 회원 id 와 커서가 그대로 서비스로 간다") {
                every { service.list(7L, 20, 30L) } returns listOf(notification(id = 5L, recipientId = 7L))

                val result = controller.list(memberId = 7L, size = 20, cursor = 30L)

                result.map { it.id } shouldBe listOf(5L)
                verify { service.list(memberId = 7L, size = 20, cursor = 30L) }
            }
        }

        When("알림에 부가 데이터가 있으면") {
            Then("JSON 문자열이 아니라 맵으로 나간다 (실시간 알림과 같은 모양)") {
                every { service.list(1L, 20, null) } returns
                    listOf(notification(data = """{"roomId":"3","messageId":"m1"}"""))

                val result = controller.list(memberId = 1L, size = 20, cursor = null)

                result.first().data shouldBe mapOf("roomId" to "3", "messageId" to "m1")
            }
        }

        When("부가 데이터가 없으면") {
            Then("빈 맵으로 나간다 (null 이 아니다)") {
                every { service.list(1L, 20, null) } returns listOf(notification(data = null))

                controller.list(memberId = 1L, size = 20, cursor = null).first().data shouldBe emptyMap()
            }
        }
    }

    Given("알림을 읽음 처리할 때") {

        // 경로의 {id} 는 알림 id 고 소유자는 @MemberId 다. 둘을 바꿔 넘기면 남의 알림이 읽음 처리된다.
        When("내 알림이면") {
            Then("인증된 회원 id 와 알림 id 가 그 순서로 서비스에 전달된다") {
                every { service.markRead(7L, 42L) } returns Unit

                controller.markRead(memberId = 7L, id = 42L)

                verify { service.markRead(memberId = 7L, id = 42L) }
            }
        }

        When("남의 알림이면") {
            Then("서비스의 403 을 그대로 올린다 (컨트롤러가 삼키지 않는다)") {
                every { service.markRead(7L, 42L) } throws LanglezException(FORBIDDEN, "notification.forbidden")

                val ex = shouldThrow<LanglezException> { controller.markRead(memberId = 7L, id = 42L) }

                ex.status.value() shouldBe 403
            }
        }
    }

    Given("알림 수신 설정을 조회할 때") {
        When("인증된 회원 id 로 물으면") {
            Then("그 회원의 설정이 응답 DTO 로 조립된다") {
                every { service.settingsOf(7L) } returns NotificationSettingSnapshot(
                    mutedTypes = setOf("CHAT_MESSAGE"),
                    quietFrom = LocalTime.of(22, 0),
                    quietTo = LocalTime.of(7, 0),
                    timeZone = "Asia/Seoul",
                )

                val result = controller.getSettings(memberId = 7L)

                result.mutedTypes shouldBe setOf("CHAT_MESSAGE")
                result.quietFrom shouldBe LocalTime.of(22, 0)
                result.timeZone shouldBe "Asia/Seoul"
                verify { service.settingsOf(7L) }
            }
        }
    }

    Given("끌 알림 유형을 바꿀 때") {
        When("인증된 회원 id 와 요청 본문이 그대로 서비스로 간다") {
            Then("응답은 서비스가 돌려준 전체 교체 결과를 그대로 담는다") {
                every { service.updateMutes(7L, setOf("CHAT_MESSAGE")) } returns setOf("CHAT_MESSAGE")

                val result = controller.updateMutes(
                    memberId = 7L,
                    request = NotificationMuteUpdateRequest(types = setOf("CHAT_MESSAGE")),
                )

                result.mutedTypes shouldBe setOf("CHAT_MESSAGE")
                verify { service.updateMutes(memberId = 7L, types = setOf("CHAT_MESSAGE")) }
            }
        }

        When("알 수 없는 유형이면") {
            Then("서비스의 400 을 그대로 올린다") {
                every { service.updateMutes(7L, setOf("UNKNOWN")) } throws
                    LanglezException(BAD_REQUEST, "notification.type.unknown")

                val ex = shouldThrow<LanglezException> {
                    controller.updateMutes(memberId = 7L, request = NotificationMuteUpdateRequest(types = setOf("UNKNOWN")))
                }

                ex.status.value() shouldBe 400
            }
        }
    }

    Given("방해금지 시간대를 바꿀 때") {
        When("인증된 회원 id 와 요청 본문이 그대로 서비스로 간다") {
            Then("응답은 저장된 방해금지 시간대를 담는다") {
                every { service.updateQuietHours(7L, LocalTime.of(22, 0), LocalTime.of(7, 0), "Asia/Seoul") } returns
                    NotificationSetting(memberId = 7L, quietFrom = LocalTime.of(22, 0), quietTo = LocalTime.of(7, 0), timeZone = "Asia/Seoul")

                val result = controller.updateQuietHours(
                    memberId = 7L,
                    request = NotificationQuietHoursUpdateRequest(
                        from = LocalTime.of(22, 0),
                        to = LocalTime.of(7, 0),
                        timeZone = "Asia/Seoul",
                    ),
                )

                result.from shouldBe LocalTime.of(22, 0)
                result.to shouldBe LocalTime.of(7, 0)
                result.timeZone shouldBe "Asia/Seoul"
            }
        }

        When("from 만 주고 to 를 안 주면") {
            Then("서비스의 400 을 그대로 올린다") {
                every { service.updateQuietHours(7L, LocalTime.of(22, 0), null, null) } throws
                    LanglezException(BAD_REQUEST, "notification.quiet-hours.incomplete")

                val ex = shouldThrow<LanglezException> {
                    controller.updateQuietHours(
                        memberId = 7L,
                        request = NotificationQuietHoursUpdateRequest(from = LocalTime.of(22, 0), to = null, timeZone = null),
                    )
                }

                ex.status.value() shouldBe 400
            }
        }
    }
})
