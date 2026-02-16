plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(libs.dependency.springboot.starter)
    api(libs.dependency.jackson)
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
