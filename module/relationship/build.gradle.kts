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

    implementation(project(":module:relationship-api"))

    // 신고 이벤트(ChatUserReportedEvent)를 @KafkaListener 로 받는다
    implementation(project(":module:chat-api"))

    // 팔로워/차단 목록에 회원 handle·프로필 이미지를 붙인다. 계약(MemberReader)만 본다
    implementation(project(":module:member-api"))

    ksp(libs.dependency.querydsl.ksp)

    // TestRelationshipApplication 이 Storage 대역을 올린다
    testImplementation(project(":module:attachment-api"))

    // 통합테스트가 members 행을 직접 넣는다(팔로우·차단 행의 FK 대상). 프로덕션 코드는 member 를 모른다
    testImplementation(project(":module:member"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
