package com.langlez.consumer

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.langlez.web.exception.LanglezException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@RestController
class CauseChainController {

    @GetMapping("/cause/langlez-500-data")
    fun langlez500Data(): String =
        throw LanglezException(HttpStatus.INTERNAL_SERVER_ERROR, "error.unexpected", cause = DataIntegrityViolationException("duplicate"))

    @GetMapping("/cause/langlez-503-optimistic")
    fun langlez503Optimistic(): String =
        throw LanglezException(HttpStatus.SERVICE_UNAVAILABLE, "error.unexpected", cause = OptimisticLockingFailureException("stale"))

    @GetMapping("/cause/langlez-502-security")
    fun langlez502Security(): String =
        throw LanglezException(HttpStatus.BAD_GATEWAY, "error.unexpected", cause = AccessDeniedException("denied"))

    @GetMapping("/cause/langlez-500-validation")
    fun langlez500Validation(): String =
        throw LanglezException(HttpStatus.INTERNAL_SERVER_ERROR, "error.unexpected", cause = ConstraintViolationException("invalid", emptySet()))

    @GetMapping("/cause/langlez-404-data")
    fun langlez404Data(): String =
        throw LanglezException(HttpStatus.NOT_FOUND, "common.not-found", cause = DataIntegrityViolationException("duplicate"))

    @GetMapping("/cause/rse-503-data")
    fun rse503Data(): String =
        throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "error.unexpected", DataIntegrityViolationException("duplicate"))

    @GetMapping("/cause/wrapped-data")
    fun wrappedData(): String = throw IllegalStateException("wrapped", DataIntegrityViolationException("duplicate"))

    @GetMapping("/cause/wrapped-validation")
    fun wrappedValidation(): String = throw IllegalStateException("wrapped", ConstraintViolationException("invalid", emptySet()))

    @GetMapping("/cause/wrapped-langlez")
    fun wrappedLanglez(): String = throw IllegalStateException("wrapped", LanglezException(HttpStatus.NOT_FOUND, "common.not-found"))
}

@SpringBootTest(classes = [ConsumerApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CauseChainTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Value($$"${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private val client = HttpClient.newHttpClient()

    private val logs = ListAppender<ILoggingEvent>().apply { start() }

    init {
        val webLogger = LoggerFactory.getLogger("com.langlez.web") as Logger

        fun get(path: String): Pair<Int, Map<*, *>> {
            if (!webLogger.isAttached(logs)) webLogger.addAppender(logs)
            logs.list.clear()
            val request = HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Accept-Language", "ko").GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return response.statusCode() to jsonMapper.readValue(response.body(), Map::class.java)
        }

        fun Pair<Int, Map<*, *>>.shouldBe(status: Int, message: String) {
            first shouldBe status
            second shouldBe mapOf("error" to true, "message" to message)
        }

        val unexpected = "서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
        val notFound = "요청하신 페이지 또는 정보를 찾을 수 없습니다."
        val conflict = "이미 사용 중인 정보입니다. 다른 값을 입력해 주세요."
        val invalid = "입력값이 올바르지 않습니다. 다시 확인해 주세요."

        Given("cause 를 가진 5xx 의도한 예외") {
            listOf(
                "/cause/langlez-500-data" to 500,
                "/cause/langlez-503-optimistic" to 503,
                "/cause/langlez-502-security" to 502,
                "/cause/langlez-500-validation" to 500,
                "/cause/rse-503-data" to 503,
            ).forEach { (path, status) ->
                When("$path 를 요청하면") {
                    val reply = get(path)
                    Then("자기 상태와 키로 답한다") { reply.shouldBe(status, unexpected) }
                    Then("스택과 함께 ERROR 로그를 남긴다") {
                        logs.list shouldHaveSize 1
                        logs.list.single().level shouldBe Level.ERROR
                        (logs.list.single().throwableProxy != null) shouldBe true
                    }
                }
            }
        }

        Given("cause 를 가진 4xx 의도한 예외") {
            When("LanglezException 404 의 cause 가 DataIntegrityViolationException 이면") {
                val reply = get("/cause/langlez-404-data")
                Then("자기 상태와 키로 답한다") { reply.shouldBe(404, notFound) }
                Then("로그를 남기지 않는다") { logs.list.shouldBeEmpty() }
            }
        }

        Given("일반 예외가 감싼 예외") {
            listOf(
                Triple("/cause/wrapped-data", 409, conflict),
                Triple("/cause/wrapped-validation", 400, invalid),
                Triple("/cause/wrapped-langlez", 404, notFound),
            ).forEach { (path, status, message) ->
                When("$path 를 요청하면") {
                    val reply = get(path)
                    Then("cause 를 따라 답한다") { reply.shouldBe(status, message) }
                    Then("로그를 남기지 않는다") { logs.list.shouldBeEmpty() }
                }
            }
        }
    }
}
