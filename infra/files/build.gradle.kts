plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.dependency.springboot.web)
    implementation(libs.dependency.springboot.jpa)
    implementation(libs.dependency.aws.s3)

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.test.kotest)
}
