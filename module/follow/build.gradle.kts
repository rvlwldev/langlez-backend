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

    // @KafkaListener 로 member-blocked 를 받는다
    implementation(project(":infra:kafka"))

    implementation(project(":module:follow-api"))

    // 차단 관계면 팔로우를 막는다. 차단당한 쪽의 팔로우를 끊는 이벤트도 여기 계약이다.
    // module:block 을 직접 참조하지 않는다
    implementation(project(":module:block-api"))

    // 팔로워/팔로잉 목록에 회원 handle·프로필 이미지를 붙인다. 계약(MemberReader)만 본다
    implementation(project(":module:member-api"))

    ksp(libs.dependency.querydsl.ksp)

    // TestFollowApplication 이 Storage 대역을 올린다
    testImplementation(project(":module:attachment-api"))

    // 통합테스트가 MemberReader 구현체를 필요로 한다(팔로우 대상 존재 확인).
    // 프로덕션 코드는 member 를 모른다
    testImplementation(project(":module:member"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
