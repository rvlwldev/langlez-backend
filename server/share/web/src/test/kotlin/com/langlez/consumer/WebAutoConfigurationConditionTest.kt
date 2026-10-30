package com.langlez.consumer

import com.langlez.web.WebAutoConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.ImportSelector
import org.springframework.core.type.AnnotationMetadata
import org.springframework.mock.web.MockServletContext

class WebAutoConfigurationSelector : ImportSelector {

    override fun selectImports(importingClassMetadata: AnnotationMetadata): Array<String> =
        arrayOf(WebAutoConfiguration::class.java.name)
}

@Configuration
@Import(WebAutoConfigurationSelector::class)
class WebAutoConfigurationHolder

class WebAutoConfigurationConditionTest : BehaviorSpec({

    fun contextWith(classLoader: ClassLoader?) =
        AnnotationConfigWebApplicationContext().apply {
            setServletContext(MockServletContext())
            classLoader?.let { setClassLoader(it) }
            register(WebAutoConfigurationHolder::class.java)
            refresh()
        }

    Given("Spring Security, DAO, Bean Validation 클래스가 있는 classpath") {
        When("자동설정을 올리면") {
            val context = contextWith(null)
            Then("공통·Security·Data·Validation 핸들러가 모두 만들어진다") {
                context.containsBean("intendedExceptionHandler") shouldBe true
                context.containsBean("globalExceptionHandler") shouldBe true
                context.containsBean("securityExceptionHandler") shouldBe true
                context.containsBean("dataExceptionHandler") shouldBe true
                context.containsBean("validationExceptionHandler") shouldBe true
            }
        }
    }

    Given("Spring Security, DAO, Bean Validation 클래스가 없는 classpath") {
        When("자동설정을 올리면") {
            val context = contextWith(FilteredClassLoader("org.springframework.security", "org.springframework.dao", "jakarta.validation"))
            Then("기동되고 공통 핸들러만 만들어진다") {
                context.containsBean("messageSource") shouldBe true
                context.containsBean("localeResolver") shouldBe true
                context.containsBean("intendedExceptionHandler") shouldBe true
                context.containsBean("globalExceptionHandler") shouldBe true
                context.containsBean("securityExceptionHandler") shouldBe false
                context.containsBean("dataExceptionHandler") shouldBe false
                context.containsBean("validationExceptionHandler") shouldBe false
            }
        }
    }
})
