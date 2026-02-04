plugins {
    alias(libs.plugins.springboot)
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":module:member"))
    implementation(project(":module:auth"))
    implementation(project(":common:exception"))
    implementation(project(":common:jackson"))
    implementation(project(":common:observability"))
    implementation(project(":common:logger"))
    implementation(project(":common:swagger"))

    implementation(project(":infra:mysql"))
    implementation(project(":infra:mongo"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:kafka"))
    implementation(project(":infra:files"))

    implementation(libs.dependency.springboot.web)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    developmentOnly(libs.development.springboot.devtools)

    testImplementation(libs.test.kotest.spring)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // testImplementation("org.testcontainers:junit-jupiter") // Removed explicit junit-jupiter
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
    // Use testImplementation for Spring Boot Test Support
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("io.rest-assured:rest-assured:5.3.2")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}
repositories {
    mavenCentral()
}
