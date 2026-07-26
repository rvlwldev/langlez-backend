package com.langlez.interest.infrastructure

import com.langlez.interest.domain.Interest
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * 언어 컬럼 12개 각각에 단일 컬럼 FULLTEXT 인덱스를 기동 시 확인 후 없으면 생성한다.
 * Flyway/Liquibase가 없는 이 프로젝트에서 JPA 어노테이션만으로는 FULLTEXT를 선언할 수 없어
 * native DDL로 보완한다. `information_schema.STATISTICS`로 이미 있으면 건너뛴다(멱등).
 */
@Component
@Order(Int.MAX_VALUE) // ddl-auto=update로 테이블 생성이 끝난 뒤 실행되도록 가장 늦게
class InterestFullTextIndexRunner(private val em: EntityManager) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        Interest.LOCALE_FIELDS.forEach { field ->
            val column = camelToSnake(field)
            val indexName = "FT_INTEREST_${column.uppercase()}"
            val exists = (
                em.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'interests' AND index_name = :indexName"
                ).setParameter("indexName", indexName).singleResult as Number
                ).toLong() > 0

            if (!exists) {
                log.info("Creating FULLTEXT index {} on interests.{}", indexName, column)
                em.createNativeQuery("ALTER TABLE interests ADD FULLTEXT INDEX $indexName ($column)").executeUpdate()
            }
        }
    }

    private fun camelToSnake(s: String): String =
        s.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}
