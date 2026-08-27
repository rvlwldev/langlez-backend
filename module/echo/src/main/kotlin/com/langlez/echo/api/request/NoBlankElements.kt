package com.langlez.echo.api.request

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * 리스트 원소가 공백이 아닌지 검사한다.
 *
 * `List<@NotBlank String>` 처럼 타입 인자에 직접 다는 방식은 Kotlin 이 기본적으로
 * TYPE_USE 애노테이션을 바이트코드에 남기지 않아(`-Xemit-jvm-type-annotations` 없이는) 조용히
 * 무시된다. 그래서 필드 레벨 제약으로 감싼다.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NoBlankElementsValidator::class])
annotation class NoBlankElements(
    val message: String = "must not contain blank elements",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NoBlankElementsValidator : ConstraintValidator<NoBlankElements, List<String>> {
    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean =
        value == null || value.none { it.isBlank() }
}
