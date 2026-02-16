plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    // 모듈 의존성만 - 외부 라이브러리는 하위 모듈에서 api()로 transitive
    implementation(project(":module:member"))
    implementation(project(":module:auth"))
    implementation(project(":common:exception"))
    implementation(project(":common:jackson"))
    implementation(project(":common:observability"))
    implementation(project(":common:swagger"))
    implementation(project(":infra:mysql"))
    implementation(project(":infra:mongo"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:files"))

    // devtools만 개발용으로 직접 선언
    developmentOnly(libs.development.springboot.devtools)
}
