plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    api(project(":common:exception"))

    api(libs.dependency.swagger3)
    api(libs.dependency.springboot.web)
    api(libs.dependency.springboot.validation)
}
