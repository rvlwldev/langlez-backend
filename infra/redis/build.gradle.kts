dependencies {
    api(libs.dependency.springboot.redis)
    api(libs.dependency.springboot.cache)
    implementation(libs.dependency.caffeine)
    implementation(libs.dependency.aspectj)
    runtimeOnly(libs.dependency.aspectj.runtime)
    implementation(project(":common:jackson"))

    testImplementation(libs.test.springboot) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "junit", module = "junit")
    }

    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
