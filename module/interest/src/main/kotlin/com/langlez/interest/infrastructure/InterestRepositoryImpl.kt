package com.langlez.interest.infrastructure

import com.langlez.interest.domain.Interest
import com.langlez.interest.domain.InterestRepository
import com.langlez.interest.infrastructure.jpa.InterestJpaRepository
import jakarta.persistence.EntityManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class InterestRepositoryImpl(
    private val jpa: InterestJpaRepository,
    private val em: EntityManager,
) : InterestRepository {

    private val allowedColumns = Interest.LOCALE_FIELDS.toSet()

    override fun findById(id: Long): Interest? = jpa.findByIdOrNull(id)

    override fun findByColumn(localeField: String, value: String): Interest? {
        require(localeField in allowedColumns) { "invalid locale field: $localeField" }
        val results = em.createQuery(
            "SELECT i FROM Interest i WHERE i.$localeField = :value",
            Interest::class.java,
        ).setParameter("value", value).setMaxResults(1).resultList
        return results.firstOrNull()
    }

    override fun save(interest: Interest): Interest = jpa.save(interest)

    override fun delete(interest: Interest) = jpa.delete(interest)

    override fun searchByColumn(localeField: String, term: String, limit: Int): List<Interest> {
        require(localeField in allowedColumns) { "invalid locale field: $localeField" }
        val column = camelToSnake(localeField)
        return em.createNativeQuery(
            "SELECT * FROM interests WHERE MATCH($column) AGAINST(:term IN NATURAL LANGUAGE MODE) LIMIT :limit",
            Interest::class.java,
        ).setParameter("term", term).setParameter("limit", limit).resultList as List<Interest>
    }

    private fun camelToSnake(s: String): String =
        s.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
}
