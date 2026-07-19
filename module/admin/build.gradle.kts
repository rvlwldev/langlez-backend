plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":module:member"))
    implementation(project(":module:chat"))
    implementation(project(":module:attachment"))
    implementation(project(":infra:redis"))
    implementation(libs.dependency.springboot.thymeleaf)

    testImplementation(project(":infra:mysql"))
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.springboot.security)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.test.mockk)
}
