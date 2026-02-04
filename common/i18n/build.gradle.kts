plugins {
    alias(libs.plugins.springboot)
    kotlin("plugin.spring")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = true
}

dependencies {
    api(libs.dependency.springboot.web)
    implementation(kotlin("stdlib"))
}
