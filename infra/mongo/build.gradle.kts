plugins {
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    api(libs.dependency.springboot.mongodb)
    api(libs.dependency.querydsl.mongodb)
    implementation(project(":common:observability"))
}
