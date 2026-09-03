// 자기 테이블이 없는 조합 모듈이다. infra:rdb·kotlin.jpa·ksp 를 넣지 않는다 —
// 엔티티가 없으므로 QueryDSL Q타입도 JPA 플러그인도 쓸 데가 없다.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))

    // 후보 목록 캐시. CacheProvider 는 core 계약이고 구현은 infra:redis 가 app/api 에서 올린다
    implementation(project(":core"))

    implementation(project(":module:matching-api"))

    // 매칭 입력. 전부 계약만 본다 — 상대 모듈의 저장소를 직접 읽지 않는다
    implementation(project(":module:lang-api"))
    implementation(project(":module:member-api"))
    implementation(project(":module:block-api"))
    implementation(project(":module:follow-api"))
}
