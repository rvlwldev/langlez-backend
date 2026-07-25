plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":infra:mysql"))
    implementation(project(":infra:redis"))

    implementation(project(":module:member"))
    implementation(project(":module:relationship"))
    implementation(project(":module:wavechat"))

    implementation(libs.dependency.springboot.websocket)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.test.mockk)
}
