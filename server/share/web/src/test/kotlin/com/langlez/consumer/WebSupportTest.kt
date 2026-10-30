package com.langlez.consumer

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.langlez.web.I18N as WebI18N
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Properties

@SpringBootTest(classes = [ConsumerApp::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSupportTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Value($$"${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private val client = HttpClient.newHttpClient()

    private val logs = ListAppender<ILoggingEvent>().apply { start() }

    init {
        val webLogger = LoggerFactory.getLogger("com.langlez.web") as Logger

        fun call(
            method: String,
            path: String,
            language: String? = null,
            body: String? = null,
            contentType: String = "application/json",
        ): Pair<Int, Map<*, *>> {
            if (!webLogger.isAttached(logs)) webLogger.addAppender(logs)
            logs.list.clear()
            val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header("Accept", "application/json")
                .header("Content-Type", contentType)
                .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            language?.let { builder.header("Accept-Language", it) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            return response.statusCode() to jsonMapper.readValue(response.body(), Map::class.java)
        }

        fun get(path: String, language: String? = null) = call("GET", path, language)

        fun Pair<Int, Map<*, *>>.shouldBe(status: Int, message: String) {
            first shouldBe status
            second shouldBe mapOf("error" to true, "message" to message)
        }

        val badRequest = mapOf(
            "ko" to "요청을 처리할 수 없습니다. 다시 시도해 주세요.",
            "ja" to "リクエストを処理できませんでした。もう一度お試しください。",
            "zh_CN" to "无法处理您的请求，请重试。",
            "zh_TW" to "無法處理您的請求，請再試。",
            "en" to "We couldn’t process your request. Please try again.",
        )

        Given("Accept-Language 헤더가 있을 때") {
            mapOf(
                "ko" to "ko",
                "ja" to "ja",
                "zh-TW" to "zh_TW",
                "zh-Hant" to "zh_TW",
                "zh-Hant-TW" to "zh_TW",
                "zh-HK" to "zh_TW",
                "zh-CN" to "zh_CN",
                "zh" to "zh_CN",
                "zh-Hans" to "zh_CN",
                "xx" to "en",
                "xx,ko;q=0.8" to "ko",
                "ko;q=0,ja;q=0.5" to "ja",
                "ko_KR" to "en",
            ).forEach { (header, language) ->
                When("Accept-Language: $header 로 요청하면") {
                    val reply = get("/page?page=abc", header)
                    Then("$language 문장으로 답한다") { reply.shouldBe(400, badRequest.getValue(language)) }
                }
            }
            When("헤더 없이 요청하면") {
                val reply = get("/page?page=abc")
                Then("en 문장으로 답한다") { reply.shouldBe(400, badRequest.getValue("en")) }
            }
        }

        Given("언어 파일에 없는 키일 때") {
            When("ko 로 요청하면") {
                val reply = get("/langlez?status=400&key=${I18N.CONSUMER.ONLY_EN}", "ko")
                Then("en 문장으로 대체한다") { reply.shouldBe(400, "Only in English.") }
            }
        }

        Given("소비 모듈 키와 share:web 키일 때") {
            When("메시지 인자를 넘기면") {
                val reply = get("/langlez?status=404&key=${I18N.CONSUMER.GREETING}&arg=Mina", "ko")
                Then("인자가 채워진다") { reply.shouldBe(404, "안녕하세요, Mina님!") }
            }
            When("share:web 키를 던지면") {
                val reply = get("/langlez?status=409&key=${WebI18N.COMMON.CONFLICT}", "ja")
                Then("같은 MessageSource 로 번역된다") { reply.shouldBe(409, "この情報はすでに使用中です。別の内容をお試しください。") }
            }
        }

        Given("예외 종류별 응답") {
            val cases = listOf(
                Triple("LanglezException 4xx", { get("/langlez?status=404&key=common.not-found") }, 404 to "The page or content you’re looking for doesn’t exist."),
                Triple("LanglezException 5xx", { get("/langlez?status=503&key=error.unexpected") }, 503 to "Something went wrong on our end. Please try again in a moment."),
                Triple("본문 파싱 실패", { call("POST", "/signup", body = "{") }, 400 to badRequest.getValue("en")),
                Triple("필수 파라미터 누락", { get("/page") }, 400 to badRequest.getValue("en")),
                Triple("타입 변환 실패", { get("/page?page=abc") }, 400 to badRequest.getValue("en")),
                Triple("없는 경로", { get("/nowhere") }, 404 to "The API endpoint you’re looking for doesn’t exist."),
                Triple("허용 안 된 메서드", { call("POST", "/langlez", body = "{}") }, 405 to "This request method isn’t allowed."),
                Triple("지원 안 하는 Content-Type", { call("POST", "/signup", body = "x", contentType = "text/plain") }, 415 to "This request format isn’t supported."),
                Triple("ResponseStatusException 키", { get("/rse?status=404&reason=common.not-found") }, 404 to "The page or content you’re looking for doesn’t exist."),
                Triple("ResponseStatusException 일반 문장", { get("/rse?status=418&reason=plain-reason") }, 418 to "plain-reason"),
                Triple("AuthenticationException", { get("/unauthorized") }, 401 to "Please log in to continue."),
                Triple("AccessDeniedException", { get("/forbidden") }, 403 to "Access denied."),
                Triple("DataIntegrityViolationException", { get("/data-conflict") }, 409 to "This information is already in use. Please try something different."),
                Triple("낙관적 락 실패", { get("/optimistic") }, 409 to "Someone else made a change at the same time. Please refresh and try again."),
                Triple("처리되지 않은 예외", { get("/boom") }, 500 to "Something went wrong on our end. Please try again in a moment."),
            )
            cases.forEach { (name, request, expected) ->
                When(name) {
                    val reply = request()
                    Then("${expected.first} 와 번역된 message 만 담긴 body 로 답한다") { reply.shouldBe(expected.first, expected.second) }
                }
            }
        }

        Given("검증 실패") {
            When("@Valid 본문이 여러 필드에서 틀리면") {
                val reply = call("POST", "/signup", "ko", """{"name":"","title":"abcd"}""")
                Then("필드별 번역 문장을 이어 붙인다") {
                    reply.first shouldBe 400
                    reply.second.keys shouldBe setOf("error", "message")
                    reply.second["error"] shouldBe true
                    val message = reply.second["message"] as String
                    message shouldContain "name: 이름을 입력해 주세요."
                    message shouldContain "title: 제목은 3자 이하여야 합니다."
                }
            }
            When("파라미터 제약이 틀리면") {
                val reply = get("/size?size=0", "ja")
                Then("파라미터 이름과 번역 문장으로 답한다") { reply.shouldBe(400, "size: サイズは1以上にしてください。") }
            }
        }

        Given("처리하는 예외의 로그") {
            listOf(
                "/langlez?status=404&key=common.not-found",
                "/rse?status=404&reason=common.not-found",
                "/page?page=abc",
                "/nowhere",
                "/unauthorized",
                "/forbidden",
                "/data-conflict",
                "/optimistic",
                "/size?size=0",
            ).forEach { path ->
                When("4xx 인 $path 를 요청하면") {
                    get(path)
                    Then("로그를 남기지 않는다") { logs.list.shouldBeEmpty() }
                }
            }
            listOf(
                "/langlez?status=500&key=error.unexpected",
                "/rse?status=503&reason=common.not-found",
                "/boom",
            ).forEach { path ->
                When("5xx 인 $path 를 요청하면") {
                    get(path)
                    Then("스택과 함께 ERROR 로그를 남긴다") {
                        logs.list shouldHaveSize 1
                        logs.list.single().level shouldBe Level.ERROR
                        (logs.list.single().throwableProxy != null) shouldBe true
                    }
                }
            }
        }

        Given("필터에서 난 에러") {
            When("필터가 예외를 던지면") {
                val reply = get("/filter-boom", "ko")
                Then("/error 를 거쳐도 같은 형식으로 답한다") { reply.shouldBe(500, "서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.") }
            }
            When("필터가 상태 코드만 보내면") {
                val reply = get("/filter-forbidden", "ko")
                Then("상태 코드를 유지하고 같은 형식으로 답한다") { reply.shouldBe(403, "접근 권한이 없습니다.") }
            }
        }

        Given("share:web 의 언어 파일") {
            fun keys(language: String): Set<Any> {
                val properties = Properties()
                javaClass.getResourceAsStream("/i18n/web/messages_$language.properties")!!
                    .reader(Charsets.UTF_8).use { properties.load(it) }
                return properties.keys.toSet()
            }
            val expected = setOf(
                WebI18N.COMMON.UNEXPECTED,
                WebI18N.COMMON.INVALID_ARGUMENT,
                WebI18N.COMMON.BAD_REQUEST,
                WebI18N.COMMON.NOT_FOUND,
                WebI18N.COMMON.NOT_FOUND_ENDPOINT,
                WebI18N.COMMON.CONFLICT,
                WebI18N.COMMON.OPTIMISTIC_LOCK_FAILURE,
                WebI18N.COMMON.UNAUTHORIZED,
                WebI18N.COMMON.FORBIDDEN,
                WebI18N.COMMON.METHOD_NOT_ALLOWED,
                WebI18N.COMMON.UNSUPPORTED_MEDIA_TYPE,
            )
            listOf("ko", "en", "ja", "zh_CN", "zh_TW", "es", "fr", "de", "pt", "ru", "vi", "id").forEach { language ->
                When("$language 파일을 읽으면") {
                    Then("I18N.COMMON 의 키를 모두 가진다") { keys(language) shouldBe expected }
                }
            }
        }
    }
}
