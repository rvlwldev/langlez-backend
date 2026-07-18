plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:mysql"))
    implementation(project(":infra:mongo"))

    implementation(project(":module:member"))
    implementation(project(":module:profile"))
    implementation(project(":module:relationship"))
    implementation(project(":module:chat"))

    implementation(libs.dependency.springboot.websocket)

    testImplementation(project(":infra:files"))
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.bundles.testcontainers)
}
