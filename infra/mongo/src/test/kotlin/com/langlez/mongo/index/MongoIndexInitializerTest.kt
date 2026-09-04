package com.langlez.mongo.index

import com.langlez.redis.distributedLock.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.IndexOperations
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.scheduling.annotation.Scheduled

@Document(collection = "test_alpha")
@CompoundIndex(name = "IDX_TEST_ALPHA_NAME", def = "{ 'name': 1 }")
internal class TestAlpha(val name: String) {
    @Id
    var id: String? = null
}

@Document(collection = "test_beta")
@CompoundIndex(name = "IDX_TEST_BETA_NAME", def = "{ 'name': -1 }")
internal class TestBeta(val name: String) {
    @Id
    var id: String? = null
}

/**
 * `@Scheduled` 만 붙고 `@DistributedLock` 이 빠지면 인스턴스 수만큼 동시에 인덱스 생성을 시도하고,
 * 인덱스 생성 실패가 그대로 전파되면 스케줄러 스레드가 죽어 재시도 기회가 사라진다.
 *
 * 도메인 엔티티를 알지 않는 일반화 버전이라, "엔티티가 하나도 없는 컨텍스트에서 무해한가"와
 * "한 엔티티의 실패가 나머지를 막지 않는가"도 함께 고정한다.
 */
class MongoIndexInitializerTest : BehaviorSpec({

    // 실제 빈은 MongoCustomConversions 의 simple type 정보(Instant 등)를 갖고 초기화된다.
    // 빈 것을 그대로 두면 Instant 를 임베드 엔티티로 오인해 private 생성자에 리플렉션으로
    // 접근하려다 JPMS 에 막힌다.
    fun mappingContext(vararg entities: Class<*>) = MongoMappingContext().apply {
        setSimpleTypeHolder(MongoCustomConversions(emptyList<Any>()).simpleTypeHolder)
        setInitialEntitySet(entities.toSet())
        initialize()
    }

    val ensureIndexes = MongoIndexInitializer::class.java.getDeclaredMethod("ensureIndexes")

    Given("ensureIndexes() 를 보면") {
        Then("60초마다 도는 @Scheduled 가 붙어 있다") {
            ensureIndexes.getAnnotation(Scheduled::class.java).shouldNotBeNull().fixedDelay shouldBe 60_000L
        }

        Then("중복 시도를 줄이는 @DistributedLock 이 함께 붙어 있다") {
            ensureIndexes.getAnnotation(DistributedLock::class.java)
                .shouldNotBeNull().prefix shouldBe "lock:mongo-index"
        }
    }

    Given("Mongo 엔티티가 하나도 없는 컨텍스트에서 ensureIndexes() 를 부르면") {
        val template = mockk<MongoTemplate>()
        val initializer = MongoIndexInitializer(template, mappingContext())

        Then("Mongo 를 아예 건드리지 않는다") {
            initializer.ensureIndexes()

            verify(exactly = 0) { template.indexOps(any<Class<*>>()) }
        }
    }

    Given("Mongo 에 인덱스를 못 만드는 상태에서 ensureIndexes() 를 부르면") {
        val template = mockk<MongoTemplate>()
        every { template.indexOps(TestAlpha::class.java) } throws IllegalStateException("mongo down")

        val initializer = MongoIndexInitializer(template, mappingContext(TestAlpha::class.java))

        Then("예외를 밖으로 던지지 않는다") {
            // ensureIndexes() 가 던지면 이 Then 자체가 실패한다 — 실패를 삼키고 다음 주기로 미루는지 확인.
            initializer.ensureIndexes()
        }
    }

    Given("엔티티 두 개 중 하나만 인덱스 생성에 실패하면") {
        val betaOps = mockk<IndexOperations>(relaxed = true)
        val template = mockk<MongoTemplate>()
        every { template.indexOps(TestAlpha::class.java) } throws IllegalStateException("mongo down")
        every { template.indexOps(TestBeta::class.java) } returns betaOps

        val initializer = MongoIndexInitializer(
            template,
            mappingContext(TestAlpha::class.java, TestBeta::class.java),
        )

        Then("실패한 엔티티가 나머지 엔티티의 인덱스 생성을 막지 않는다") {
            initializer.ensureIndexes()

            verify(exactly = 1) { betaOps.createIndex(any()) }
        }
    }

    Given("한 주기가 실패한 뒤 Mongo 가 회복되면") {
        val alphaOps = mockk<IndexOperations>(relaxed = true)
        val template = mockk<MongoTemplate>()
        every { template.indexOps(TestAlpha::class.java) } throws IllegalStateException("mongo down")

        val initializer = MongoIndexInitializer(template, mappingContext(TestAlpha::class.java))
        initializer.ensureIndexes()

        Then("완료 플래그가 서지 않아 다음 주기에 인덱스가 만들어진다") {
            every { template.indexOps(TestAlpha::class.java) } returns alphaOps

            initializer.ensureIndexes()

            verify(exactly = 1) { alphaOps.createIndex(any()) }
        }
    }
})
