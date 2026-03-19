plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common:jackson"))
    implementation(project(":infra:mysql"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:kafka"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.bundles.testcontainers)
}
