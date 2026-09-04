plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // MongoTemplate / @Document / MongoRepository 를 소비 모듈이 그대로 쓴다
    api(libs.dependency.springboot.mongodb)

    // MongoIndexInitializer 가 @DistributedLock 으로 인스턴스 중복 시도를 줄인다
    implementation(project(":infra:redis"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.junit)
    testImplementation(libs.test.testcontainers.mongodb)
}
