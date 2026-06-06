plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(project(":core"))

    // web
    api(libs.dependency.springboot.web)
    api(libs.dependency.springboot.validation)
    api(libs.dependency.swagger3)

    // jackson
    api(libs.dependency.kotlin.jackson)
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // security + jwt
    api(libs.dependency.springboot.security)
    api(libs.dependency.springboot.oauth2.client)
    implementation(libs.bundles.jjwt)

    // observability
    api(libs.dependency.springboot.actuator)
    api(libs.dependency.springboot.aop)
    api(libs.dependency.micrometer.prometheus)
    api(libs.dependency.micrometer.tracing.brave)
    api(libs.dependency.zipkin.reporter.brave)
    api(libs.dependency.p6spy.starter)
    api(libs.dependency.kotlin.logging)
    api(libs.dependency.logstash.encoder)
}
