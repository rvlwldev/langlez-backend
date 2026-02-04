plugins {
    alias(libs.plugins.springboot)
    kotlin("plugin.spring")
    // Use the alias if available in libs.versions.toml, or specific ID with version
    // Based on the error, "org.jetbrains.kotlin.plugin.jpa" needs version if not in catalog correctly or use alias
    alias(libs.plugins.kotlin.jpa)
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":infra:mysql"))
    implementation(project(":common:exception"))
    implementation(project(":common:observability"))

    api("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(kotlin("stdlib"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.mockk)
}
