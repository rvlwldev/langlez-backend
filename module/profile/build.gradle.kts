plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.ksp)
}

dependencies {
    implementation(project(":common:web"))
    implementation(project(":common:security"))
    implementation(project(":common:observability"))

    implementation(project(":core"))
    implementation(project(":infra:mysql"))
    implementation(project(":infra:redis"))

    implementation(project(":module:member"))

    ksp(libs.dependency.querydsl.ksp)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.bundles.testcontainers)
}
