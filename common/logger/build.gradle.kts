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
    api("org.springframework.boot:spring-boot-starter-aop") // For AspectJ logging
    api("io.github.oshai:kotlin-logging-jvm:5.1.0")
    
    // P6Spy for SQL logging (moved from observability?)
    api(libs.dependency.p6spy.starter) 
    
    implementation(kotlin("stdlib"))
}
