plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":infra:mongo"))
    implementation(project(":infra:redis"))

    implementation(project(":module:member"))
    implementation(project(":infra:files"))

    implementation(libs.dependency.springboot.websocket)

    testImplementation(project(":infra:mysql"))
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.test.mockk)
}
