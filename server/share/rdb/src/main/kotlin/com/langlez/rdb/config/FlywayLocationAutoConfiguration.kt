package com.langlez.rdb.config

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(Flyway::class)
class FlywayLocationAutoConfiguration {

    @Bean
    fun flywayMigrationLocation() = FlywayConfigurationCustomizer { it.locations(MIGRATION_LOCATION) }

    companion object {
        const val MIGRATION_LOCATION = "classpath:migration"
    }
}
