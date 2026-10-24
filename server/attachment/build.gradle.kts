plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":share:auth"))
    implementation(project(":share:rdb"))
    implementation(project(":share:jackson"))

    implementation(libs.dependency.springboot.web)
    implementation(libs.dependency.aws.s3)
    implementation(libs.dependency.kotlin.reflect)

    testImplementation(libs.test.springboot)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.junit)
    testImplementation(libs.test.testcontainers.postgresql)
    testImplementation(libs.test.testcontainers.minio)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
