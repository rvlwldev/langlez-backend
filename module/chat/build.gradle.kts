plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.ksp)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":infra:rdb"))
    implementation(project(":infra:redis"))

    implementation(project(":module:member"))

    implementation(libs.dependency.springboot.websocket)

    // 메시지 본문은 Mongo 에 있다. 방·참여자만 JPA 다.
    implementation(libs.dependency.springboot.mongodb)

    ksp(libs.dependency.querydsl.ksp)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.test.mockk)
}
