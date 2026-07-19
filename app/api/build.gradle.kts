plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":infra:mysql"))
    implementation(project(":infra:mongo"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:files"))
    implementation(project(":module:member"))
    implementation(project(":module:profile"))
    implementation(project(":module:auth"))
    implementation(project(":module:relationship"))
    implementation(project(":module:echo"))
    implementation(project(":module:chat"))
    implementation(project(":module:admin"))
    implementation(project(":module:matching"))
    implementation(project(":module:wave"))
    implementation(project(":module:notification"))
    implementation(project(":module:attachment"))
    implementation(project(":module:report"))
    implementation(project(":module:wavechat"))

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
