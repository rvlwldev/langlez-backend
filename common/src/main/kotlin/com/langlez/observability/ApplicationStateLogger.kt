package com.langlez.observability

import java.time.LocalDateTime
import org.springframework.boot.context.event.ApplicationFailedEvent
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component

@Component
class ApplicationStateLogger : ApplicationListener<ApplicationEvent> {
    private val logger = org.slf4j.LoggerFactory.getLogger(ApplicationStateLogger::class.java)

    override fun onApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is ApplicationReadyEvent -> {
                logger.run {
                    info("==========================================================================================")
                    info("  Application Started Successfully at {}", LocalDateTime.now())
                    info("==========================================================================================")
                }
            }

            is ApplicationFailedEvent -> {
                logger.run {
                    error("==========================================================================================")
                    error("  Application Failed to Start at {}", LocalDateTime.now())
                    if (event.exception != null) logger.error("  Reason: {}", event.exception.message)
                    error("==========================================================================================")
                }
            }

            is ContextClosedEvent -> {
                logger.run {
                    info("==========================================================================================")
                    info("  Application Stopping (Context Closed) at {}", LocalDateTime.now())
                    info("==========================================================================================")
                }
            }
        }
    }
}
