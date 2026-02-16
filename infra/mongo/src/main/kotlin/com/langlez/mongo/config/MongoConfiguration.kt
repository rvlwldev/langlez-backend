package com.langlez.mongo.config
import com.langlez.observability.config.LoggerProperties

import com.langlez.observability.PerformanceLogger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.MongoTransactionManager
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@AutoConfiguration
@EnableMongoRepositories(basePackages = ["com.langlez"])
@EnableMongoAuditing
class MongoConfiguration {

    private val logger = LoggerFactory.getLogger(MongoConfiguration::class.java)

    @Bean
    fun mongoTransactionManager(dbFactory: MongoDatabaseFactory) =
        MongoTransactionManager(dbFactory)

    @Bean
    fun mongoObservabilityCustomizer(logger: PerformanceLogger, properties: LoggerProperties) =
        MongoClientSettingsBuilderCustomizer { it.addCommandListener(MongoQueryLogger(logger, properties)) }
            .also { this@MongoConfiguration.logger.info("Registered Mongo Observability CommandListener successfully.") }

}
