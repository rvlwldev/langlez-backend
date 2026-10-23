plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.mongodb)

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.junit)
    testImplementation(libs.test.testcontainers.mongodb)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
