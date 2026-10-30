plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.web)

    implementation(libs.dependency.kotlin.reflect)
    implementation(libs.dependency.kotlin.logging)

    compileOnly(libs.dependency.spring.security.core)
    compileOnly(libs.dependency.spring.tx)
    compileOnly(libs.dependency.jakarta.validation)

    testImplementation(libs.dependency.springboot.validation)
    testImplementation(libs.dependency.kotlin.jackson)
    testImplementation(libs.dependency.spring.security.core)
    testImplementation(libs.dependency.spring.tx)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
}
