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

    implementation(project(":module:chat-api"))
    implementation(project(":module:member-api"))
    implementation(project(":module:relationship-api"))
    implementation(project(":module:attachment-api"))

    implementation(libs.dependency.springboot.websocket)

    // 메시지 본문은 Mongo 에 있다. 방·참여자만 JPA 다.
    implementation(libs.dependency.springboot.mongodb)

    ksp(libs.dependency.querydsl.ksp)

    // 통합테스트 컨텍스트가 OnlineTracker 구현(MemberOnlineTracker)을 필요로 한다.
    // 프로덕션 코드는 member-api 계약만 본다 — app/api 가 member 모듈을 함께 올린다.
    testImplementation(project(":module:member"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.test.mockk)
}
