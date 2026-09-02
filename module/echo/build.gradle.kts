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

    implementation(project(":module:echo-api"))
    implementation(project(":module:attachment-api"))

    // module:follow / module:block 을 직접 참조하지 않는다. 팔로우·차단은 계약 모듈의 포트로만 본다.
    implementation(project(":module:follow-api"))
    implementation(project(":module:block-api"))

    ksp(libs.dependency.querydsl.ksp)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.test.mockk)
}
