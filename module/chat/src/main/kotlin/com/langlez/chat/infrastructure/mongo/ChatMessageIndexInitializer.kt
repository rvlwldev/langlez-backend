package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.redis.distributedLock.DistributedLock
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `ChatMessage` 인덱스(`@CompoundIndex` ×3, `@Indexed` ×1)를 기동 경로 밖에서 만든다.
 *
 * **버린 방식 1 — `spring.data.mongodb.auto-index-creation: true` 유지.**
 * `MongoTemplate` 빈 생성 시점(컨텍스트 refresh 중)에 인덱스를 동기로 쏜다. Mongo 가 응답하지 않으면
 * 드라이버 기본 서버 선택 타임아웃(30초)까지 블로킹한 뒤 `chatMessageMongoRepository` →
 * `chatService` → `chatController` 의존 체인이 무너져 컨텍스트 refresh 자체가 취소된다.
 * `chat` 은 `app/api` 가 항상 조립하므로 회피 경로가 없다 (README §5.1-3).
 *
 * **버린 방식 2 — 서버 선택 타임아웃만 짧게 잡기.**
 * 블로킹 시간은 줄어도 "인덱스 생성이 기동을 좌우한다"는 결함 자체는 남는다. 응답이 타임아웃
 * 안에 오면 여전히 그만큼 부팅이 늦어지고, 안 오면 여전히 컨텍스트가 취소된다. 인덱스 생성을
 * 기동 경로 밖으로 아예 빼는 쪽이 근본 해결이다.
 *
 * **택한 방식.** `auto-index-creation` 을 끄고(`application.yml`), 컨텍스트가 뜬 뒤 별도 스케줄로
 * 인덱스를 만든다. 인덱스 정의는 `ChatMessage` 의 애노테이션 한 곳에만 있고, 여기서는
 * `auto-index-creation` 이 내부적으로 쓰는 것과 같은 `MongoPersistentEntityIndexResolver` 로 그
 * 애노테이션을 그대로 읽어 적용한다 — 정의를 스크립트나 마이그레이션에 다시 적지 않는다.
 *
 * 실패해도 앱을 막지 않는다: 로그만 남기고 다음 주기에 재시도한다. `createIndex` 는 같은 이름·정의의
 * 인덱스가 이미 있으면 그대로 no-op 이라 멱등이다 — 여러 인스턴스가 동시에 시도해도 안전하지만,
 * 불필요한 왕복을 줄이려 `@DistributedLock` 으로 한
 * 인스턴스만 시도하게 한다 (module/CLAUDE.md §5 — `@Scheduled` 는 `@DistributedLock` 병행).
 * 한 번 성공하면 이후 주기는 로컬 플래그로 즉시 반환해 비용이 없다.
 */
@Component
internal class ChatMessageIndexInitializer(
    private val template: MongoTemplate,
    private val mappingContext: MongoMappingContext,
) {

    private val created = AtomicBoolean(false)

    @Scheduled(fixedDelay = 60_000, initialDelay = 0)
    @DistributedLock(prefix = "lock:chat-message-index", throwOnFailure = false)
    fun ensureIndexes() {
        if (created.get()) return

        runCatching {
            val entity = mappingContext.getRequiredPersistentEntity(ChatMessage::class.java)
            val definitions = MongoPersistentEntityIndexResolver(mappingContext).resolveIndexForEntity(entity)
            val indexOps = template.indexOps(ChatMessage::class.java)
            definitions.forEach { indexOps.createIndex(it) }
        }.onSuccess {
            created.set(true)
            log.info("ChatMessage 인덱스 생성 완료")
        }.onFailure { e ->
            log.warn("ChatMessage 인덱스 생성 실패, 다음 주기에 재시도한다", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatMessageIndexInitializer::class.java)
    }
}
