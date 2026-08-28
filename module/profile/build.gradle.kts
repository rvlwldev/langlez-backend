plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.ksp)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":infra:rdb"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:kafka"))

    ksp(libs.dependency.querydsl.ksp)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.bundles.testcontainers)
}
