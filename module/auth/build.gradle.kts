plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:rdb"))

    // @KafkaListener 로 member-withdrawn 을 받는다
    implementation(project(":infra:kafka"))

    implementation(project(":module:member"))
    implementation(project(":module:member-api"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
