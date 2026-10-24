package com.langlez.rdb.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class RdbDefaultProperties : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        environment.propertySources.addLast(MapPropertySource(NAME, DEFAULTS))
    }

    companion object {
        const val NAME = "langlezRdbDefaults"
        const val STORE_ASSIGNMENT_LOG_LEVEL =
            "logging.level.org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport"

        private val DEFAULTS: Map<String, Any> = mapOf(STORE_ASSIGNMENT_LOG_LEVEL to "WARN")
    }
}
