plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.web)
    api(libs.dependency.swagger3)
    api(libs.dependency.springboot.security)
    api(libs.dependency.jackson)
    api(libs.dependency.springboot.validation)
    api(libs.dependency.springboot.jpa)
    api(project(":common:exception"))
}
