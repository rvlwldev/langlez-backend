package com.langlez.consumer

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant

enum class Channel { PUSH, EMAIL }

data class MemberJoined(
    val memberId: Long,
    val handle: String,
    val nickname: String? = null,
    val channels: List<Channel> = emptyList(),
    val joinedAt: Instant,
)

@SpringBootTest(classes = [ConsumerApp::class])
class JsonMapperTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @Autowired
    lateinit var objectMapper: ObjectMapper

    init {
        Given("share:jackson 에 의존하면") {
            Then("Jackson 3 최신 버전의 JsonMapper 빈 하나가 ObjectMapper 로도 주입된다") {
                objectMapper shouldBe jsonMapper
                jsonMapper.version().toString() shouldBe "3.2.4"
            }
        }

        Given("코틀린 데이터 클래스 이벤트를") {
            val event = MemberJoined(
                memberId = 7,
                handle = "hj",
                channels = listOf(Channel.PUSH),
                joinedAt = Instant.parse("2026-10-24T05:30:15.123Z"),
            )

            When("직렬화하면") {
                val json = jsonMapper.writeValueAsString(event)

                Then("시각은 ISO-8601 문자열로 쓴다") {
                    json shouldContain "\"joinedAt\":\"2026-10-24T05:30:15.123Z\""
                }

                Then("다시 읽으면 같은 값이 된다") {
                    jsonMapper.readValue<MemberJoined>(json) shouldBe event
                }
            }
        }

        Given("발행 쪽이 필드를 더 넣거나 기본값이 있는 필드를 뺀 이벤트를") {
            val json = """{"memberId":7,"handle":"hj","joinedAt":"2026-10-24T05:30:15Z","addedLater":true}"""

            When("읽으면") {
                Then("모르는 필드는 무시하고 빠진 필드는 코틀린 기본값으로 채운다") {
                    jsonMapper.readValue<MemberJoined>(json) shouldBe MemberJoined(
                        memberId = 7,
                        handle = "hj",
                        joinedAt = Instant.parse("2026-10-24T05:30:15Z"),
                    )
                }
            }
        }
    }
}
