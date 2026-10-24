plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.jackson)
    api(libs.dependency.kotlin.jackson)

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
}
