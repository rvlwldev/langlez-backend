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

    // @KafkaListener 로 chat-message-sent 를 받는다
    implementation(project(":infra:kafka"))

    implementation(project(":module:notification-api"))

    // 알림거리가 되는 사건을 받는 쪽이다. 발행 모듈의 계약만 물고 모듈 자체는 참조하지 않는다.
    implementation(project(":module:chat-api"))
    implementation(project(":module:member-api"))
    implementation(project(":module:follow-api"))

    implementation("com.google.firebase:firebase-admin:9.4.1")

    ksp(libs.dependency.querydsl.ksp)

    // TestNotificationApplication 이 TokenManager 용 RedissonClient 대역을 올린다
    testImplementation(libs.dependency.redisson)

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.bundles.test.kotest)
    testImplementation(libs.test.mockk)
}
