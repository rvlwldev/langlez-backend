plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.dependency.springboot.web)
    implementation(libs.dependency.kotlin.reflect)
    implementation(libs.dependency.kotlin.logging)

    compileOnly(libs.dependency.spring.security.core)
    compileOnly("org.springframework:spring-tx")

    testImplementation(libs.dependency.springboot.validation)
    testImplementation(libs.dependency.kotlin.jackson)
    testImplementation(libs.dependency.spring.security.core)
    testImplementation("org.springframework:spring-tx")
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
}
