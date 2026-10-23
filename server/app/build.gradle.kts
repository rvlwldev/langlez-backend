plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":share:auth"))
    implementation(project(":share:redis"))
    implementation(project(":share:rdb"))
    implementation(project(":share:mongodb"))
    implementation(project(":share:kafka"))

    implementation(libs.dependency.kotlin.reflect)

    implementation("org.springframework:spring-tx")
    implementation(libs.dependency.springboot.web)
    implementation("org.springframework:spring-messaging")
    implementation(libs.dependency.springboot.validation)
    implementation(libs.dependency.swagger3)
    implementation(libs.dependency.kotlin.jackson)
    implementation(libs.dependency.springboot.actuator)
    implementation(libs.dependency.springboot.aop)
    implementation(libs.dependency.micrometer.prometheus)
    implementation(libs.dependency.micrometer.tracing.brave)
    implementation(libs.dependency.zipkin.reporter.brave)
    implementation(libs.dependency.p6spy.starter)
    implementation(libs.dependency.kotlin.logging)
    implementation(libs.dependency.logstash.encoder)

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.junit)
    testImplementation(libs.test.testcontainers.postgresql)
    testImplementation(libs.test.testcontainers.mongodb)
    testImplementation(libs.test.testcontainers.kafka)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
