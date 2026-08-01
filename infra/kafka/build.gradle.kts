plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))

    // KafkaTemplate / @KafkaListener 를 소비 모듈이 그대로 쓴다
    api(libs.dependency.spring.kafka)

    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.kafka)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner)
    testImplementation(libs.test.springboot) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.mockito")
    }

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
