plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common:exception"))
    implementation(project(":common:security"))
    implementation(project(":common:observability"))
    implementation(project(":common:web"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:mysql"))

    implementation(project(":module:member"))
}
