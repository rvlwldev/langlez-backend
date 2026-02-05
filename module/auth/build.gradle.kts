plugins {
    alias(libs.plugins.springboot)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
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
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}
