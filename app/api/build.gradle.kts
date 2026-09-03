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
    implementation(project(":module:follow"))
    implementation(project(":module:block"))
    implementation(project(":module:report"))
    implementation(project(":module:echo"))
    implementation(project(":module:chat"))
    implementation(project(":module:wave"))
    implementation(project(":module:notification"))
    implementation(project(":module:attachment"))
    implementation(project(":module:lang"))

    developmentOnly(libs.development.springboot.devtools)

    // *-api 계약 모듈은 따로 등록하지 않는다. 빈이 없는 인터페이스·DTO 뿐이고,
    // 각 도메인 모듈이 implementation 으로 물어 런타임 클래스패스에 그대로 올라온다.
    testImplementation(project(":module:member-api"))
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
