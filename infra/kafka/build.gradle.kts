plugins {
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    api(libs.dependency.springboot.starter)
    api(libs.dependency.spring.kafka)
}
