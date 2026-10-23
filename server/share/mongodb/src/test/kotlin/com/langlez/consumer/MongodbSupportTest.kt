package com.langlez.consumer

import com.langlez.demo.mongo.Note
import com.langlez.demo.mongo.NoteRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.mongodb.MongoDBContainer

@SpringBootTest(classes = [ConsumerApp::class])
class MongodbSupportTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repository: NoteRepository

    companion object {
        private val mongo = MongoDBContainer("mongo:6.0").also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.mongodb.uri") { mongo.replicaSetUrl }
        }
    }

    init {
        Given("앱 패키지 밖의 mongo 패키지에 리포지토리가 있을 때") {
            When("저장하고 조회하면") {
                Then("리포지토리가 등록돼 있어 저장과 조회가 된다") {
                    val saved = repository.save(Note(text = "안녕"))

                    saved.id.shouldNotBeNull()
                    repository.findByText("안녕").map { it.text } shouldBe listOf("안녕")
                }
            }
        }
    }
}
