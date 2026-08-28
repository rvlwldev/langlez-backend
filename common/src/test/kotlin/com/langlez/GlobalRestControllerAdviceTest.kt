package com.langlez

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.core.MethodParameter
import org.springframework.validation.MapBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import java.util.Locale

/**
 * handleValidationException 이 defaultMessage 를 resolveMessage 로 통과시키는지 검증한다.
 * i18n 키를 그대로 응답에 흘리던 버그(CLAUDE.md §5)의 회귀 테스트다.
 */
class GlobalRestControllerAdviceTest : BehaviorSpec({

    val messageSource = ResourceBundleMessageSource().apply {
        setBasename("messages")
        setDefaultEncoding("UTF-8")
    }
    val advice = GlobalRestControllerAdvice(messageSource)

    val parameter = MethodParameter(
        GlobalRestControllerAdvice::class.java.getMethod(
            "handleValidationException",
            MethodArgumentNotValidException::class.java,
            Locale::class.java,
        ),
        0,
    )

    fun exceptionOf(field: String, defaultMessage: String): MethodArgumentNotValidException {
        val bindingResult = MapBindingResult(HashMap<String, Any>(), "update")
        bindingResult.addError(FieldError("update", field, defaultMessage))
        return MethodArgumentNotValidException(parameter, bindingResult)
    }

    Given("검증 실패 필드의 defaultMessage 가 i18n 키일 때") {
        When("응답을 만들면") {
            val response = advice.handleValidationException(exceptionOf("bio", "validation.member.bio.size"), Locale.KOREAN)

            Then("키 원문이 아니라 번역된 문장이 담긴다") {
                val message = response.body!!.message
                message shouldNotContain "validation.member.bio.size"
                message shouldBe "bio: 자기소개는 200자 이내여야 합니다."
            }
        }
    }

    Given("검증 실패 필드의 defaultMessage 가 i18n 키가 아닌 리터럴 문자열일 때 (module/auth 의 기존 관례)") {
        When("응답을 만들면") {
            val response = advice.handleValidationException(
                exceptionOf("refreshToken", "Refresh token cannot be blank"),
                Locale.KOREAN,
            )

            Then("resolveMessage 의 키 조회 실패 폴백으로 원래 문자열이 그대로 나간다") {
                response.body!!.message shouldBe "refreshToken: Refresh token cannot be blank"
            }
        }
    }
})
