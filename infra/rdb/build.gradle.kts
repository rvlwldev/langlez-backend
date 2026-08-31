plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.ksp)
}

dependencies {
    api(project(":core"))
    api(project(":common"))

    // OutBox 공통 베이스가 생성자로 KafkaTemplate 을 받는다. 상속 모듈에서 타입이 보여야 한다
    api(libs.dependency.spring.kafka)

    api(libs.dependency.springboot.jpa)

    // 스키마는 Flyway 가 만든다. Hibernate 는 validate 만 한다.
    api(libs.dependency.flyway.core)
    runtimeOnly(libs.dependency.flyway.postgresql)
    api(libs.dependency.querydsl.jpa)

    // OutBox 베이스가 @MappedSuperclass라, 상속받는 모듈의 Q클래스가 참조할 부모 Q클래스를 여기서 생성한다
    ksp(libs.dependency.querydsl.ksp)
    // 검색 통합테스트 전용 엔티티(SearchDocument)의 Q클래스를 테스트 소스셋에도 생성한다
    kspTest(libs.dependency.querydsl.ksp)

    runtimeOnly(libs.runtimeonly.postgresql)

    // TestRdbApplication 이 JwtAuthenticationFilter 용 MemberQuery 대역을 올린다
    testImplementation(project(":module:member-api"))

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.test.testcontainers)
    testImplementation(libs.test.testcontainers.junit)
    testImplementation(libs.test.testcontainers.postgresql)
}
