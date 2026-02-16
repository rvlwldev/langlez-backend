plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // Spring Boot Starter Web (for RestControllerAdvice)
    implementation(libs.dependency.springboot.web)

    // Kotlin Logging
    implementation(libs.dependency.kotlin.reflect)
}
