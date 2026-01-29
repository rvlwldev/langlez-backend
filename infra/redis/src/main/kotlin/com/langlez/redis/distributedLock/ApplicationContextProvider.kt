package com.langlez.redis.distributedLock

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

/**
 * Spring ApplicationContext에 접근하기 위한 Helper 클래스
 */
@Component
class ApplicationContextProvider : ApplicationContextAware {
    companion object {
        private var applicationContext: ApplicationContext? = null

        fun getApplicationContext(): ApplicationContext? = applicationContext
    }

    override fun setApplicationContext(context: ApplicationContext) {
        applicationContext = context
    }
}
