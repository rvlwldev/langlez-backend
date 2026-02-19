plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.kotlin.coroutine)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation(project(":module:member"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:mysql"))
    implementation(project(":common:security"))
    implementation(project(":common:exception"))
    implementation(project(":common:observability"))

    implementation(libs.dependency.springboot.validation)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.restassured)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.test.testcontainers.redis)
    testImplementation(project(":infra:files"))
}
