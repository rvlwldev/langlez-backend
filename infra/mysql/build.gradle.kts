plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.ksp)
}

dependencies {
    // OutBox 공통 베이스가 core.MessageQueue를 사용한다
    api(project(":core"))

    api(libs.dependency.springboot.jpa)
    api(libs.dependency.querydsl.jpa)

    // OutBox 베이스가 @MappedSuperclass라, 상속받는 모듈의 Q클래스가 참조할 부모 Q클래스를 여기서 생성한다
    ksp(libs.dependency.querydsl.ksp)

    runtimeOnly(libs.runtimeonly.mysql)
}
