package com.langlez.observability.bridge.mongo

import com.langlez.logger.PerformanceLogger
import com.langlez.logger.config.LoggerProperties
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MongoObservabilityConfiguration {
    @Bean
    fun mongoObservabilityCustomizer(
        performanceLogger: PerformanceLogger,
        properties: LoggerProperties,
    ): MongoClientSettingsBuilderCustomizer =
        MongoClientSettingsBuilderCustomizer { builder ->
            builder.addCommandListener(MongoQueryLogger(performanceLogger, properties))
        }
}
