plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common:observability"))
    implementation(project(":infra:mysql"))
    implementation(project(":infra:mongo"))
    implementation(project(":infra:redis"))
    implementation(project(":module:member"))
    implementation(project(":module:profile"))
    implementation(project(":module:auth"))
    implementation(project(":module:relationship"))
    implementation(project(":module:outbox"))
    implementation(project(":infra:kafka"))

    developmentOnly(libs.development.springboot.devtools)
}
