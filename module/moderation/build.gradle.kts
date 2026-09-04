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

    // @KafkaListener 로 chat-user-reported 를 받는다
    implementation(project(":infra:kafka"))

    implementation(project(":module:moderation-api"))

    // 신고를 판단해 회원을 정지/해제한다(MemberWriter). 계약만 물고 module:member 는 참조하지 않는다
    implementation(project(":module:member-api"))

    // 신고 이벤트(ChatUserReportedEvent)를 받는다. 발행 모듈의 계약만 물고 모듈 자체는 참조하지 않는다
    implementation(project(":module:chat-api"))

    ksp(libs.dependency.querydsl.ksp)

    // TestModerationApplication 이 Storage 대역을 올린다
    testImplementation(project(":module:attachment-api"))

    // 계약(MemberReader/MemberWriter)의 구현 빈이 필요하다. 프로덕션 코드는 계약만 안다
    testImplementation(project(":module:member"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
