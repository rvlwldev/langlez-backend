plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        // Spring Boot 4.1 은 Spring Cloud 2025.1.x(Oakwood) 와 맞는다
        mavenBom(libs.dependency.springcloud.bom.get().toString())
    }
}

dependencies {
    implementation(libs.dependency.springcloud.config.server)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
