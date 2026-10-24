package com.langlez.attachment

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class AttachmentPropertiesTest : BehaviorSpec({

    Given("접근 키가 있는 설정이 있을 때") {
        val properties = AttachmentProperties(
            endpoint = "https://storage.example.com",
            region = "auto",
            bucket = "attachments",
            accessKey = "access-key-value",
            secretKey = "secret-key-value",
            publicBaseUrl = "https://cdn.example.com/",
        )

        When("문자열로 바꾸면") {
            val text = properties.toString()

            Then("접근 키와 비밀 키는 찍히지 않고 나머지 설정은 보인다") {
                text shouldNotContain "access-key-value"
                text shouldNotContain "secret-key-value"
                text shouldContain "attachments"
                text shouldContain "https://storage.example.com"
            }
        }

        When("공개 URL 을 만들면") {
            Then("기본 URL 끝의 슬래시를 정리해 붙인다") {
                properties.publicUrl("profile/a.jpg") shouldBe "https://cdn.example.com/profile/a.jpg"
            }
        }
    }
})
