plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common:exception"))
    implementation(libs.dependency.springboot.web)
    implementation(libs.bundles.jjwt)

    api(libs.dependency.springboot.security)
    api(libs.dependency.springboot.oauth2.client)
}
