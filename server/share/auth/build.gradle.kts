plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.oauth2.resource)

    implementation(libs.dependency.kotlin.reflect)

    testImplementation(libs.dependency.springboot.web)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
}
