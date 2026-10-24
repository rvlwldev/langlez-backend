package com.langlez.rdb.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@AutoConfiguration
@ConditionalOnClass(EntityManager::class, JPAQueryFactory::class)
@EnableJpaAuditing
class RdbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun jpaQueryFactory(entityManager: EntityManager) = JPAQueryFactory(entityManager)
}
