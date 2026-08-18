plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    api(project(":core")) // Cache/CacheProvider 를 공개 시그니처로 노출한다

    implementation(libs.dependency.aspectj)
    implementation(libs.dependency.caffeine)
    api(libs.dependency.redisson)
    runtimeOnly(libs.dependency.aspectj.runtime)

    api(libs.dependency.springboot.redis)
    // 브로드캐스터가 SimpMessagingTemplate 을 공개 생성자로 받는다
    api(libs.dependency.springboot.websocket)

    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner)
    testImplementation(libs.test.springboot) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.mockito")
    }

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
