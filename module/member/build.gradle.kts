plugins {
    alias(libs.plugins.springboot)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":infra:mysql"))
    implementation(project(":common:exception"))
    implementation(project(":common:observability"))

    api(libs.dependency.springboot.jpa)
    implementation(libs.dependency.springboot.web)
    implementation(libs.dependency.spring.security.core)
    implementation(libs.dependency.springboot.validation)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.restassured)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.test.testcontainers.redis)
    testImplementation(project(":common:security"))
}
