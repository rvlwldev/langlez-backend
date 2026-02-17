plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.starter)
    api(libs.dependency.spring.kafka)

    implementation(project(":common:observability"))

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner)
}
