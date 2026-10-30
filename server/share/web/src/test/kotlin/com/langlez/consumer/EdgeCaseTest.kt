package com.langlez.consumer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.mock.web.MockServletContext
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

data class PageForm(val page: Int)
data class NameForm(val name: String)

@RestController
class ModelAttributeController {

    @GetMapping("/form-page")
    fun page(@ModelAttribute form: PageForm): String = "ok"

    @GetMapping("/form-name")
    fun name(@ModelAttribute form: NameForm): String = "ok"
}

@ComponentScan(basePackages = ["com.langlez.web"])
class RootPackageScanHolder

class RootPackageScanTest : BehaviorSpec({

    Given("com.langlez 를 스캔하는 앱과 Security, DAO, Bean Validation 이 없는 classpath") {
        When("컨텍스트를 올리면") {
            val context = AnnotationConfigWebApplicationContext().apply {
                setServletContext(MockServletContext())
                setClassLoader(FilteredClassLoader("org.springframework.security", "org.springframework.dao", "jakarta.validation"))
                register(RootPackageScanHolder::class.java)
                refresh()
            }
            Then("없는 클래스의 핸들러는 만들어지지 않는다") {
                context.containsBean("securityExceptionHandler") shouldBe false
                context.containsBean("dataExceptionHandler") shouldBe false
                context.containsBean("validationExceptionHandler") shouldBe false
            }
        }
    }
})

@SpringBootTest(classes = [ConsumerApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EdgeCaseTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Value($$"${local.server.port}")
    private var port: Int = 0

    init {
        val badRequest = """{"message":"요청을 처리할 수 없습니다. 다시 시도해 주세요.","error":true}"""
        val badRequestEn = """{"message":"We couldn’t process your request. Please try again.","error":true}"""

        fun get(path: String, language: String): HttpResponse<String> =
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Accept-Language", language).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        Given("@ModelAttribute 바인딩 실패") {
            When("타입 변환에 실패하면") {
                val response = get("/form-page?page=abc", "ko")
                Then("common.bad-request 로 답한다") { (response.statusCode() to response.body()) shouldBe (400 to badRequest) }
            }
            When("Kotlin 생성자 인자가 없으면") {
                val response = get("/form-name", "ko")
                Then("common.bad-request 로 답한다") { (response.statusCode() to response.body()) shouldBe (400 to badRequest) }
            }
        }

        Given("형식이 깨진 Accept-Language") {
            When("- 로 요청하면") {
                val response = get("/page?page=abc", "-")
                Then("en 문장의 JSON 으로 답한다") { (response.statusCode() to response.body()) shouldBe (400 to badRequestEn) }
            }
        }
    }
}

@SpringBootTest(classes = [ConsumerApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["spring.web.error.path=/custom-error"])
class CustomErrorPathTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Value($$"${local.server.port}")
    private var port: Int = 0

    init {
        Given("spring.web.error.path 를 바꾼 앱") {
            When("필터가 예외를 던지면") {
                val response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI("http://localhost:$port/filter-boom")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                Then("500 을 유지한다") { response.statusCode() shouldBe 500 }
            }
        }
    }
}
