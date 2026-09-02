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

    implementation(project(":module:report-api"))

    // 신고 이벤트(ChatUserReportedEvent)를 받는다. 발행 모듈의 계약만 물고 모듈 자체는 참조하지 않는다
    implementation(project(":module:chat-api"))

    ksp(libs.dependency.querydsl.ksp)

    // TestReportApplication 이 Storage 대역을 올린다
    testImplementation(project(":module:attachment-api"))

    // common 의 인증 필터가 MemberReader 빈을 요구한다. 프로덕션 코드는 member 를 모른다
    testImplementation(project(":module:member"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
