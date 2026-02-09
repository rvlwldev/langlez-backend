plugins {
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    api(libs.dependency.springboot.redis)
    api(libs.dependency.springboot.cache)
    implementation(libs.dependency.caffeine)
    implementation(libs.dependency.aspectj)
    runtimeOnly(libs.dependency.aspectj.runtime)
    implementation(project(":common:jackson"))
    implementation(project(":common:observability"))

    testImplementation(libs.test.springboot) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.mockito")
    }

    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // Needed for Kotest runner
}
