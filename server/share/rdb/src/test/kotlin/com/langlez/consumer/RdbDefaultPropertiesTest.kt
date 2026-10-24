package com.langlez.consumer

import com.langlez.rdb.config.RdbDefaultProperties
import com.langlez.rdb.config.RdbDefaultProperties.Companion.STORE_ASSIGNMENT_LOG_LEVEL
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class RdbDefaultPropertiesTest : BehaviorSpec({

    Given("앱이 Spring Data 저장소 판별 로그 레벨을 정하지 않았을 때") {
        val environment = StandardEnvironment()

        When("share:rdb 기본값을 적용하면") {
            RdbDefaultProperties().postProcessEnvironment(environment, SpringApplication())

            Then("WARN 이 된다") {
                environment.getProperty(STORE_ASSIGNMENT_LOG_LEVEL) shouldBe "WARN"
            }
        }
    }

    Given("앱이 그 로그 레벨을 직접 정했을 때") {
        val environment = StandardEnvironment().apply {
            propertySources.addFirst(MapPropertySource("app", mapOf(STORE_ASSIGNMENT_LOG_LEVEL to "DEBUG")))
        }

        When("share:rdb 기본값을 적용해도") {
            RdbDefaultProperties().postProcessEnvironment(environment, SpringApplication())

            Then("앱이 정한 값이 이긴다") {
                environment.getProperty(STORE_ASSIGNMENT_LOG_LEVEL) shouldBe "DEBUG"
            }
        }
    }
})
