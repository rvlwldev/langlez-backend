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
    implementation(project(":core"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:rdb"))

    // 팔로워/차단 목록에 회원 handle·프로필 이미지를 붙인다
    implementation(project(":module:member"))

    ksp(libs.dependency.querydsl.ksp)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
