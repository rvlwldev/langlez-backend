package com.langlez.i18n

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.MessageSource
import org.springframework.context.support.DelegatingMessageSource
import java.util.Locale

/**
 * i18n 이 실제로 해석되는지 지키는 회귀 테스트.
 *
 * `MessageSourceAutoConfiguration` 은 로케일 접미사가 없는 `messages.properties` 가 클래스패스에
 * 존재할 때만 `messageSource` 빈을 만든다. 그 파일이 없으면 빈이 아예 안 생기고
 * `GlobalRestControllerAdvice` 가 키로 폴백해 i18n 키 문자열이 그대로 응답 본문에 나간다.
 * 실제로 그 상태로 배포돼 있었고, 12개 번들이 멀쩡했기 때문에 아무도 눈치채지 못했다.
 *
 * ### 왜 여기(app/api)인가
 * 이건 번들 파일의 내용이 아니라 **조립된 앱의 자동설정이 켜지느냐**의 문제다.
 * `common` 에 `ResourceBundleMessageSource` 를 직접 만들어 확인하는 테스트는 파일이 사라져도
 * 초록으로 뜬다 — 조건 자체를 안 보기 때문이다. 컨테이너는 필요 없으니
 * `ApplicationContextRunner` 로 자동설정만 띄운다.
 */
class MessageSourceAutoConfigurationTest : BehaviorSpec({

    // 오류 응답에 실려 나가는 대표 키들. 계정 상태 차단이 쓰는 키를 포함한다.
    val keys = listOf("member.suspended", "member.withdrawn", "member.not-found", "auth.forbidden", "profile.not-found")

    // 점으로 이어진 소문자 = 번역되지 않고 새어 나온 i18n 키의 모양.
    val looksLikeKey = Regex("^[a-z][a-z0-9]*(\\.[a-z0-9-]+)+$")

    val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration::class.java))
        .withPropertyValues("spring.messages.basename=messages", "spring.messages.encoding=UTF-8")

    Given("조립된 앱과 같은 설정으로 메시지 자동설정을 올릴 때") {

        When("messageSource 빈을 찾으면") {
            // 이름만 보면 안 된다. 자동설정이 꺼져도 AbstractApplicationContext 가
            // 같은 이름으로 DelegatingMessageSource 를 심어두기 때문에 초록으로 뜬다.
            Then("DelegatingMessageSource 가 아닌 실제 번들 소스가 올라온다") {
                runner.run { context ->
                    val source = context.getBean("messageSource", MessageSource::class.java)
                    (source is DelegatingMessageSource) shouldBe false
                }
            }
        }

        When("오류 응답에 쓰이는 키를 조회하면") {
            Then("영어 번역문이 나온다") {
                runner.run { context ->
                    val source = context.getBean(MessageSource::class.java)
                    keys.forEach { key ->
                        source.getMessage(key, null, Locale.ENGLISH) shouldNotBe key
                    }
                }
            }

            Then("키 문자열이 그대로 새어 나오지 않는다") {
                runner.run { context ->
                    val source = context.getBean(MessageSource::class.java)
                    keys.forEach { key ->
                        looksLikeKey.matches(source.getMessage(key, null, Locale.ENGLISH)) shouldBe false
                    }
                }
            }

            Then("한국어 번들도 해석된다") {
                runner.run { context ->
                    val source = context.getBean(MessageSource::class.java)
                    keys.forEach { key ->
                        source.getMessage(key, null, Locale.KOREAN) shouldNotBe key
                    }
                }
            }
        }
    }
})
