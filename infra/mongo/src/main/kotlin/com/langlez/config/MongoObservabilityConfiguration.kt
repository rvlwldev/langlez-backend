package com.langlez.config

import com.langlez.logger.PerformanceLogger
import com.langlez.logger.config.LoggerProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class MongoObservabilityConfiguration {
    private val logger = LoggerFactory.getLogger(MongoObservabilityConfiguration::class.java)

    @Bean
    open fun mongoObservabilityCustomizer(
        logger: PerformanceLogger,
        properties: LoggerProperties
    ): MongoClientSettingsBuilderCustomizer = MongoClientSettingsBuilderCustomizer { builder ->
        builder.addCommandListener(MongoQueryLogger(logger, properties))
        this@MongoObservabilityConfiguration.logger.info("Registered Mongo Observability CommandListener successfully.")
    }
}
