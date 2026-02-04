plugins {
    kotlin("plugin.spring")
}
dependencies {
    api(libs.dependency.springboot.web)
    api(libs.dependency.swagger3)
    api(libs.dependency.springboot.security)
    api(libs.dependency.jackson)
    api(libs.dependency.springboot.validation)
    api(libs.dependency.springboot.jpa)
    api(project(":common:exception"))
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}
