package com.langlez.web.config

import com.langlez.web.error.JsonErrorController
import com.langlez.web.error.TranslatedErrorAttributes
import com.langlez.web.exception.DataExceptionHandler
import com.langlez.web.exception.GlobalExceptionHandler
import com.langlez.web.exception.IntendedExceptionHandler
import com.langlez.web.exception.SecurityExceptionHandler
import com.langlez.web.exception.ValidationExceptionHandler
import com.langlez.web.i18n.AcceptLanguageLocaleResolver
import com.langlez.web.i18n.FallbackMessageSource
import jakarta.validation.ConstraintViolationException
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.webmvc.error.ErrorAttributes
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.dao.DataAccessException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.servlet.LocaleResolver

@AutoConfiguration(
    beforeName = [
        "org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
        "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration",
    ],
)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class WebAutoConfiguration {

    @Bean("messageSource")
    @ConditionalOnMissingBean(name = ["messageSource"])
    fun messageSource(): MessageSource = FallbackMessageSource(messageBasenames())

    @Bean("localeResolver")
    @ConditionalOnMissingBean(name = ["localeResolver"])
    fun localeResolver(): LocaleResolver = AcceptLanguageLocaleResolver()

    @Bean
    @ConditionalOnMissingBean
    fun intendedExceptionHandler(messageSource: MessageSource) = IntendedExceptionHandler(messageSource)

    @Bean
    @ConditionalOnMissingBean
    fun globalExceptionHandler(messageSource: MessageSource) = GlobalExceptionHandler(messageSource)

    @Bean
    @ConditionalOnMissingBean(TranslatedErrorAttributes::class)
    fun errorAttributes(messageSource: MessageSource) = TranslatedErrorAttributes(messageSource)

    @Bean
    @ConditionalOnMissingBean(ErrorController::class)
    fun jsonErrorController(errorAttributes: ErrorAttributes) = JsonErrorController(errorAttributes)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AuthenticationException::class)
    class SecurityExceptionConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun securityExceptionHandler(messageSource: MessageSource) = SecurityExceptionHandler(messageSource)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DataAccessException::class)
    class DataExceptionConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun dataExceptionHandler(messageSource: MessageSource) = DataExceptionHandler(messageSource)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ConstraintViolationException::class)
    class ValidationExceptionConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun validationExceptionHandler(messageSource: MessageSource) = ValidationExceptionHandler(messageSource)
    }

    private fun messageBasenames(): List<String> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath*:i18n/*/messages*.properties")
            .mapNotNull { BASENAME.find(it.url.toString())?.groupValues?.get(1) }
            .distinct()
            .sorted()
            .map { "i18n/$it/messages" }

    private companion object {
        val BASENAME = Regex("i18n/([^/]+)/messages[^/]*\\.properties$")
    }
}
