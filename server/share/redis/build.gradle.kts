plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(libs.dependency.redisson)
    implementation(libs.dependency.springboot.aop)
    implementation(libs.dependency.kotlin.reflect)
    implementation(libs.dependency.kotlin.jackson)
    implementation(libs.dependency.caffeine)
    implementation("org.springframework:spring-tx")
    implementation("io.micrometer:micrometer-core")

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.testcontainers)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
