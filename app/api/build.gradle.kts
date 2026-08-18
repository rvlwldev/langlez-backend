plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":infra:rdb"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:kafka"))
    implementation(project(":module:member"))
    implementation(project(":module:profile"))
    implementation(project(":module:auth"))
    implementation(project(":module:relationship"))
    implementation(project(":module:echo"))
    implementation(project(":module:chat"))
    implementation(project(":module:wave"))
    implementation(project(":module:notification"))
    implementation(project(":module:attachment"))

    developmentOnly(libs.development.springboot.devtools)

    testImplementation(project(":common"))
    testImplementation(project(":infra:redis"))
    testImplementation(libs.dependency.redisson)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.springboot.testcontainers)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.mockk)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.bundles.testcontainers)
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("APP_LOG_PATH", "${rootProject.projectDir}/app/log/langlez-server/logs")
}
