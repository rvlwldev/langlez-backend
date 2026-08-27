plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(project(":core"))

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
    // JwtTokenProvider 가 io.jsonwebtoken.Claims 를 공개 시그니처로 노출한다
    api(libs.bundles.jjwt)

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
