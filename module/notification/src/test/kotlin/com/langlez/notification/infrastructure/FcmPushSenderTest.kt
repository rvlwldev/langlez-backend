package com.langlez.notification.infrastructure

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.SendResponse
import com.langlez.notification.domain.PushSender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify

class FcmPushSenderTest : BehaviorSpec({

    val app = mockk<FirebaseApp>()
    val fcm = mockk<FirebaseMessaging>()

    beforeSpec {
        // FirebaseApp.getApps() 가 이 이름의 앱을 이미 돌려주면 initializeApp 을 안 타므로
        // 진짜 서비스 계정 키 없이도 FirebaseMessaging.getInstance 경로만 목으로 검증할 수 있다.
        every { app.name } returns "langlez-notification"
        mockkStatic(FirebaseApp::class)
        mockkStatic(FirebaseMessaging::class)
        every { FirebaseApp.getApps() } returns listOf(app)
        every { FirebaseMessaging.getInstance(app) } returns fcm
    }

    afterEach { clearMocks(fcm, answers = false) }

    afterSpec {
        unmockkStatic(FirebaseApp::class)
        unmockkStatic(FirebaseMessaging::class)
    }

    fun batchResponse(size: Int, allSuccessful: Boolean = true): BatchResponse {
        val response = mockk<BatchResponse>()
        every { response.responses } returns List(size) {
            mockk<SendResponse> { every { isSuccessful } returns allSuccessful }
        }
        return response
    }

    Given("fcm.credentials 가 설정돼 있을 때") {
        val sender = FcmPushSender(credentials = "dummy.json")

        When("토큰이 500개를 넘으면") {
            Then("sendEachForMulticast 를 청크로 나눠 2번 부른다") {
                val tokens = (1..501).map { "token-$it" }
                every { fcm.sendEachForMulticast(any()) } returnsMany listOf(batchResponse(500), batchResponse(1))

                val result = sender.sendAll(tokens, "title", "body", emptyMap())

                verify(exactly = 2) { fcm.sendEachForMulticast(any()) }
                result.failedTokens shouldBe emptyList()
                result.unregisteredTokens shouldBe emptyList()
            }
        }

        When("일부 토큰이 실패로 응답되면 (UNREGISTERED 포함)") {
            Then("실패 토큰과 UNREGISTERED 죽은 토큰을 구분하여 돌려준다") {
                val tokens = listOf("ok-1", "dead-1", "other-fail")
                val exUnregistered = mockk<FirebaseMessagingException>()
                every { exUnregistered.messagingErrorCode } returns MessagingErrorCode.UNREGISTERED
                val exOther = mockk<FirebaseMessagingException>()
                every { exOther.messagingErrorCode } returns MessagingErrorCode.INTERNAL

                val response = mockk<BatchResponse>()
                every { response.responses } returns listOf(
                    mockk<SendResponse> {
                        every { isSuccessful } returns true
                    },
                    mockk<SendResponse> {
                        every { isSuccessful } returns false
                        every { exception } returns exUnregistered
                    },
                    mockk<SendResponse> {
                        every { isSuccessful } returns false
                        every { exception } returns exOther
                    },
                )
                every { fcm.sendEachForMulticast(any()) } returns response

                val result = sender.sendAll(tokens, "title", "body", emptyMap())

                result.failedTokens shouldBe listOf("dead-1", "other-fail")
                result.unregisteredTokens shouldBe listOf("dead-1")
            }
        }

        When("토큰 목록이 비어 있으면") {
            Then("FCM 을 부르지 않고 빈 결과를 돌려준다") {
                val result = sender.sendAll(emptyList(), "title", "body", emptyMap())

                result shouldBe PushSender.PushResult()
                verify(exactly = 0) { fcm.sendEachForMulticast(any()) }
            }
        }
    }

    Given("fcm.credentials 가 비어 있을 때") {
        val sender = FcmPushSender(credentials = "")

        When("sendAll 을 호출하면") {
            Then("경고만 남기고 빈 결과를 돌려준다") {
                val result = sender.sendAll(listOf("token"), "title", "body", emptyMap())

                result shouldBe PushSender.PushResult()
                verify(exactly = 0) { fcm.sendEachForMulticast(any()) }
            }
        }
    }
})
