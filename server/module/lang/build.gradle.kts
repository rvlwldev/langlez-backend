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

    implementation(project(":module:lang-api"))

    ksp(libs.dependency.querydsl.ksp)

    // 단독 컨텍스트 기동에만 필요하다. common 의 TokenManager 가 Redisson 직결이라
    // RedissonClient 빈이 없으면 컨텍스트가 뜨지 않는다. 프로덕션 코드는 레디스를 쓰지 않는다.
    testImplementation(project(":infra:redis"))

    // TestLangApplication 이 MemberReader 대역을 올린다(JwtAuthenticationFilter 가 요구한다).
    // 프로덕션 코드는 member 를 모른다
    testImplementation(project(":module:member-api"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
