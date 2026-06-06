plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.mongodb)
    api(libs.dependency.querydsl.mongodb)
    implementation(project(":common"))
}
