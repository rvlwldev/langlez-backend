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
    api(project(":common:exception"))
    api(project(":common:logger")) // Security often needs logging
    api(project(":infra:redis")) // For Token Blacklist? Or keep it abstract? 
    // Ideally security shouldn't depend on infra:redis directly if possible, but for JwtTokenProvider it might need it.
    // Let's keep it simple for now and see if we can decouple later.
    
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-oauth2-client")
    api("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    
    implementation(kotlin("stdlib"))
}
