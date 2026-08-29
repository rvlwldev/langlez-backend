package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.redis.distributedLock.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.scheduling.annotation.Scheduled

/**
 * `@Scheduled` 만 붙고 `@DistributedLock` 이 빠지면 인스턴스 수만큼 동시에 인덱스 생성을 시도하고,
 * 인덱스 생성 실패가 그대로 전파되면 스케줄러 스레드가 죽어 재시도 기회가 사라진다.
 */
class ChatMessageIndexInitializerTest : BehaviorSpec({

    val ensureIndexes = ChatMessageIndexInitializer::class.java.getDeclaredMethod("ensureIndexes")

    Given("ensureIndexes() 를 보면") {
        Then("60초마다 도는 @Scheduled 가 붙어 있다") {
            ensureIndexes.getAnnotation(Scheduled::class.java).shouldNotBeNull().fixedDelay shouldBe 60_000L
        }

        Then("중복 시도를 줄이는 @DistributedLock 이 함께 붙어 있다") {
            ensureIndexes.getAnnotation(DistributedLock::class.java)
                .shouldNotBeNull().prefix shouldBe "lock:chat-message-index"
        }
    }

    Given("Mongo 에 인덱스를 못 만드는 상태에서 ensureIndexes() 를 부르면") {
        // 실제 빈은 MongoCustomConversions 의 simple type 정보(Instant 등)를 갖고 초기화된다.
        // 빈 것을 그대로 두면 Instant 를 임베드 엔티티로 오인해 private 생성자에 리플렉션으로
        // 접근하려다 JPMS 에 막힌다.
        val mappingContext = MongoMappingContext().apply {
            setSimpleTypeHolder(MongoCustomConversions(emptyList<Any>()).simpleTypeHolder)
            setInitialEntitySet(setOf(ChatMessage::class.java))
            initialize()
        }
        val template = mockk<MongoTemplate>()
        every { template.indexOps(ChatMessage::class.java) } throws IllegalStateException("mongo down")

        val initializer = ChatMessageIndexInitializer(template, mappingContext)

        Then("예외를 밖으로 던지지 않는다") {
            // ensureIndexes() 가 던지면 이 Then 자체가 실패한다 — 실패를 삼키고 다음 주기로 미루는지 확인.
            initializer.ensureIndexes()
        }
    }
})
