package com.langlez.rdb

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import javax.sql.DataSource

@AutoConfiguration
@ConditionalOnClass(DataSource::class)
@EnableJpaRepositories(basePackages = ["com.langlez.**.jpa"])
// 없으면 @EntityListeners(AuditingEntityListener) / @CreatedDate 가 조용히 무동작한다
@EnableJpaAuditing
class RdbConfiguration {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Bean
    fun jpaQueryFactory(): JPAQueryFactory = JPAQueryFactory(entityManager)

}