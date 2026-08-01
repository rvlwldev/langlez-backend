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
    api(libs.dependency.querydsl.jpa)

    // OutBox 베이스가 @MappedSuperclass라, 상속받는 모듈의 Q클래스가 참조할 부모 Q클래스를 여기서 생성한다
    ksp(libs.dependency.querydsl.ksp)

    runtimeOnly(libs.runtimeonly.mysql)
}
