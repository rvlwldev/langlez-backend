plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common:jackson"))
    implementation(project(":common:observability"))

    implementation(libs.dependency.aspectj)
    implementation(libs.dependency.caffeine)
    implementation(libs.dependency.redisson)
    runtimeOnly(libs.dependency.aspectj.runtime)

    api(libs.dependency.springboot.redis)
    api(libs.dependency.springboot.cache)

    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner)
    testImplementation(libs.test.springboot) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.mockito")
    }

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
