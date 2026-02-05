plugins {
    alias(libs.plugins.springboot)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    api(libs.dependency.springboot.aop)
    api(libs.dependency.kotlin.logging)
    api(libs.dependency.p6spy.starter)
}
