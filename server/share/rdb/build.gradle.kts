plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.jpa)
    api(libs.dependency.flyway.core)
    api(libs.dependency.querydsl.jpa)

    runtimeOnly(libs.dependency.flyway.postgresql)
    runtimeOnly(libs.runtimeonly.postgresql)

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.postgresql)
}
