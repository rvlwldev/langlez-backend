package com.langlez.logger

import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component

@Component
class ApplicationStateLogger : ApplicationListener<ApplicationEvent> {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun onApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is ApplicationReadyEvent -> {
                logger.run { info("===== Application Started at {} =====", LocalDateTime.now()) }
            }

            is ContextClosedEvent -> {
                logger.run { info("===== Application Stopping at {} =====", LocalDateTime.now()) }
            }

            is ApplicationFailedEvent -> {
                logger.run {
                    error("Application Failed to Start at {}", LocalDateTime.now())
                    if (event.exception != null) logger.error("Reason: {}", event.exception.message)
                }
            }
        }
    }
}
