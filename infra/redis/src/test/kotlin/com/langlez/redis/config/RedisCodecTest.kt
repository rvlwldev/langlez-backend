package com.langlez.redis.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.netty.buffer.ByteBufAllocator
import java.time.Instant

/** 캐시에 담기는 실제 형태를 흉내낸다 — 코틀린 클래스는 기본 final 이고 Instant 를 물고 있다. */
private class CachedMember(
    val id: Long = 1L,
    val handle: String = "alice",
    val createdAt: Instant = Instant.parse("2026-01-02T03:04:05Z"),
)

/**
 * 프로덕션 코덱(`RedissonConfiguration.redisCodec`)으로 실제 왕복시킨다.
 * 테스트가 다른 코덱(Kryo 등)을 쓰면 이 결함을 못 잡는다 — 반드시 프로덕션 것을 그대로 쓴다.
 */
class RedisCodecTest : BehaviorSpec({

    val codec = RedissonConfiguration.redisCodec(ObjectMapper().registerKotlinModule().findAndRegisterModules())

    Given("final 코틀린 클래스를 레디스 코덱으로 인코딩하면") {
        val original = CachedMember()
        val buf = codec.valueEncoder.encode(original)
        val json = buf.toString(Charsets.UTF_8)

        Then("타입 정보(@class)가 함께 기록된다") {
            // 없으면 디코딩이 readValue(.., Object::class) 에서 타입을 못 찾아 전부 실패한다
            json.contains("@class") shouldBe true
        }

        When("다시 디코딩하면") {
            val decoded = codec.valueDecoder.decode(
                ByteBufAllocator.DEFAULT.buffer().writeBytes(json.toByteArray()),
                null,
            )

            Then("원래 타입 그대로 돌아온다") {
                decoded.shouldBeInstanceOf<CachedMember>()
            }

            Then("Instant 필드가 보존된다") {
                (decoded as CachedMember).createdAt shouldBe original.createdAt
                decoded.handle shouldBe "alice"
            }
        }
    }
})
