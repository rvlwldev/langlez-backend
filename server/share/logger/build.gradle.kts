plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.starter)
    api(libs.dependency.kotlin.logging)

    implementation(libs.dependency.loki4j)

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.testcontainers)
}
