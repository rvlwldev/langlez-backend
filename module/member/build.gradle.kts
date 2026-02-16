plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":infra:mysql"))
    implementation(project(":common:security"))
    implementation(project(":common:exception"))
    implementation(project(":common:observability"))
    implementation(project(":infra:files"))

    implementation(libs.dependency.kotlin.coroutine)
    implementation(libs.dependency.springboot.jpa)
    implementation(libs.dependency.springboot.web)
    implementation(libs.dependency.spring.security.core)
    implementation(libs.dependency.springboot.validation)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.restassured)
    testImplementation(libs.test.testcontainers.redis)
    testImplementation(libs.bundles.testcontainers)
}
