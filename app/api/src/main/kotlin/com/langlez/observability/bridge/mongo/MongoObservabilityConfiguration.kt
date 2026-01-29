package com.langlez.observability.bridge.mongo

import com.langlez.config.ObservabilityProperties
import com.langlez.observability.PerformanceLogger
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MongoObservabilityConfiguration {
    @Bean
    fun mongoObservabilityCustomizer(
        performanceLogger: PerformanceLogger,
        properties: ObservabilityProperties,
    ): MongoClientSettingsBuilderCustomizer =
        MongoClientSettingsBuilderCustomizer { builder ->
            builder.addCommandListener(MongoQueryLogger(performanceLogger, properties))
        }
}
