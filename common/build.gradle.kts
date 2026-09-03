plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(project(":core"))

    // JwtAuthenticationFilter 가 매 요청 계정 상태를 본다. core 에 MemberStatusQuery 가 있던 시절에도
    // common → member 계약 의존은 이미 있었고 core 라는 이름 뒤에 가려져 있었을 뿐이다.
    // api 가 아니라 implementation 이다 — 이 타입을 실제로 쓰는 모듈만 각자 선언하게 둔다.
    implementation(project(":module:member-api"))

    // web
    api(libs.dependency.springboot.web)
    // WebSocketSubscriptionGate 가 STOMP 프레임을 본다. 게이트는 브로커 설정이 아니라
    // 채널 인터셉터라서 spring-websocket 없이 spring-messaging 만 있으면 된다.
    api("org.springframework:spring-messaging")
    api(libs.dependency.springboot.validation)
    api(libs.dependency.swagger3)

    // jackson
    api(libs.dependency.kotlin.jackson)
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // security + jwt
    api(libs.dependency.springboot.security)
    api(libs.dependency.springboot.oauth2.client)
    // TokenManager 가 jjwt 타입을 전부 내부에 가둔다(TokenInfo 로 감싼다). 소비 모듈은 볼 필요가 없다.
    implementation(libs.bundles.jjwt)

    // TokenManager 의 차단 저장이 Redisson 직결이다. 우리 모듈이 아니라 서드파티라
    // infra:redis → common 의존과 순환하지 않는다.
    implementation(libs.dependency.redisson)

    // observability
    api(libs.dependency.springboot.actuator)
    api(libs.dependency.springboot.aop)
    api(libs.dependency.micrometer.prometheus)
    api(libs.dependency.micrometer.tracing.brave)
    api(libs.dependency.zipkin.reporter.brave)
    api(libs.dependency.p6spy.starter)
    api(libs.dependency.kotlin.logging)
    api(libs.dependency.logstash.encoder)
}
