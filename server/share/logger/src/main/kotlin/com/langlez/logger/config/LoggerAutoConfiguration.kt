package com.langlez.logger.config

import ch.qos.logback.classic.LoggerContext
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@AutoConfiguration
@ConditionalOnClass(LoggerContext::class)
@ConditionalOnProperty(prefix = "langlez.logger.loki", name = ["push-url"])
@EnableConfigurationProperties(LokiProperties::class)
class LoggerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    internal fun lokiAppenderRegistration(properties: LokiProperties, environment: Environment) =
        LokiAppenderRegistration(properties, environment.getProperty("spring.application.name", "application"))
}
