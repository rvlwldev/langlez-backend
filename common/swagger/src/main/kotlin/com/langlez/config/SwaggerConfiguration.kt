package com.langlez.config

import com.langlez.swagger.config.SwaggerAutoConfiguration
import com.langlez.swagger.config.SwaggerSecurityConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    SwaggerAutoConfiguration::class,
    SwaggerSecurityConfiguration::class,
)
open class SwaggerConfiguration
