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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.security:spring-security-core")
    implementation(kotlin("stdlib"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.mockk)
    testImplementation(libs.test.springboot) // SpringBootTest
    testImplementation("io.rest-assured:rest-assured:5.3.2")
    testImplementation(libs.test.testcontainers)
    testImplementation("org.testcontainers:mysql")
    testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
}
