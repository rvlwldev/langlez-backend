plugins {
    alias(libs.plugins.springboot)
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":common:exception"))
    implementation(project(":common:jackson"))
    implementation(project(":common:observability"))
    implementation(project(":common:swagger"))

    implementation(project(":infra:mysql"))
    implementation(project(":infra:mongo"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:kafka"))

    implementation(libs.dependency.springboot.web)

    developmentOnly(libs.development.springboot.devtools)

    testImplementation(libs.test.kotest.spring)
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}
