package com.langlez.auth.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class OAuth2UserProfileTest : BehaviorSpec({

    Given("OAuth2UserProfile 팩토리 메서드 실행 시") {
        When("Google 프로필 생성 시 이메일이 포함되어 있으면") {
            val attributes = mapOf("sub" to "g123", "email" to "user@gmail.com", "name" to "Google User")
            val profile = OAuth2UserProfile.by("google", "sub", attributes)

            Then("정상적으로 필드가 매핑된다") {
                profile.provider shouldBe "GOOGLE"
                profile.providerKey shouldBe "sub"
                profile.email shouldBe "user@gmail.com"
                profile.displayName shouldBe "Google User"
            }
        }

        When("Apple 프로필 생성 시 이메일이 없거나 빈 문자열이면") {
            val attributes = mapOf("sub" to "a123", "email" to "")
            val profile = OAuth2UserProfile.by("apple", "sub", attributes)

            Then("email은 null로 반환되고 기본 displayName이 설정된다") {
                profile.provider shouldBe "APPLE"
                profile.providerKey shouldBe "sub"
                profile.email shouldBe null
                profile.displayName shouldBe "AppleUser"
            }
        }

        When("지원하지 않는 Provider인 경우") {
            Then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    OAuth2UserProfile.by("kakao", "k123", emptyMap())
                }
            }
        }
    }
})
