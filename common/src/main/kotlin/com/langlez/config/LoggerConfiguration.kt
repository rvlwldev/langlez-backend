package com.langlez.config

import com.langlez.logger.PerformanceLogger
import com.langlez.logger.P6SpyEventListener
import com.langlez.property.LoggerProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(LoggerProperties::class)
class LoggerConfiguration {

    @Bean
    fun p6SpyEventListener(logger: PerformanceLogger, properties: LoggerProperties): P6SpyEventListener =
        P6SpyEventListener(logger, properties)

}