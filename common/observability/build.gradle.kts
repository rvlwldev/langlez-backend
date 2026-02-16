plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.starter)
    api(libs.dependency.springboot.actuator)
    api(libs.dependency.springboot.aop)

    api(libs.dependency.micrometer.prometheus)
    api(libs.dependency.micrometer.tracing.brave)

    api(libs.dependency.p6spy.starter)
    api(libs.dependency.kotlin.logging)
    api(libs.dependency.logstash.encoder)
}
