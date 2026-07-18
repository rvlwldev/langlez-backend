package com.langlez.echo.api

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.validation.Validation
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class EchoRequestTest : BehaviorSpec({
    val validatorFactory = Validation.buildDefaultValidatorFactory()
    val validator: Validator = validatorFactory.validator

    Given("ReportPost 요청 시") {
        When("정상 사유이면") {
            val request = EchoRequest.ReportPost(reason = "정상 사유")
            val violations = validator.validate(request)

            Then("위반 사항이 없어야 한다") {
                violations.size shouldBe 0
            }
        }

        When("사유가 500자를 초과하면") {
            val request = EchoRequest.ReportPost(reason = "a".repeat(501))
            val violations = validator.validate(request)

            Then("Size 위반이 발생해야 한다") {
                violations.size shouldBe 1
                val violation = violations.first()
                violation.propertyPath.toString() shouldBe "reason"
                violation.constraintDescriptor.annotation.annotationClass shouldBe Size::class
            }
        }

        When("사유가 빈 값(blank)이면") {
            val request = EchoRequest.ReportPost(reason = "")
            val violations = validator.validate(request)

            Then("NotBlank 위반이 발생해야 한다") {
                violations.size shouldBe 1
                val violation = violations.first()
                violation.propertyPath.toString() shouldBe "reason"
                violation.constraintDescriptor.annotation.annotationClass shouldBe NotBlank::class
            }
        }
    }

    Given("CreatePost 요청 시") {
        When("본문이 1000자를 초과하면") {
            val request = EchoRequest.CreatePost(content = "a".repeat(1001), media = emptyList())
            val violations = validator.validate(request)

            Then("Size 위반이 발생해야 한다") {
                violations.size shouldBe 1
                val violation = violations.first()
                violation.propertyPath.toString() shouldBe "content"
                violation.constraintDescriptor.annotation.annotationClass shouldBe Size::class
            }
        }
    }
})
