package com.langlez.observability

import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import java.time.LocalDateTime

class ApplicationStateLogger : ApplicationListener<ApplicationEvent> {
    private val logger = org.slf4j.LoggerFactory.getLogger(ApplicationStateLogger::class.java)

    override fun onApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is ApplicationReadyEvent -> {
                logger.info(
                    "==========================================================================================",
                )
                logger.info("  Application Started Successfully at {}", LocalDateTime.now())
                logger.info(
                    "==========================================================================================",
                )
            }
            is ApplicationFailedEvent -> {
                logger.error(
                    "==========================================================================================",
                )
                logger.error("  Application Failed to Start at {}", LocalDateTime.now())
                if (event.exception != null) {
                    logger.error("  Reason: {}", event.exception.message)
                }
                logger.error(
                    "==========================================================================================",
                )
            }
            is ContextClosedEvent -> {
                logger.info(
                    "==========================================================================================",
                )
                logger.info("  Application Stopping (Context Closed) at {}", LocalDateTime.now())
                logger.info(
                    "==========================================================================================",
                )
            }
        }
    }
}
