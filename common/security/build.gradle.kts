plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    api(project(":common:exception"))
    api(project(":common:observability"))
    api(project(":infra:redis"))

    api(libs.dependency.springboot.security)
    api(libs.dependency.springboot.web)
    api(libs.dependency.springboot.oauth2.client)
    api(libs.bundles.jjwt)
}
