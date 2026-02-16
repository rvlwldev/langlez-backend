plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    api(libs.dependency.springboot.jpa)
    api(libs.dependency.querydsl.jpa)

    runtimeOnly(libs.runtimeonly.mysql)
}
