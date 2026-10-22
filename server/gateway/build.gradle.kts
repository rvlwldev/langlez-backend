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

// WebFlux 기반이라 서블릿(WebMVC) 기반인 common·core·infra·module 에 의존하지 않는다
dependencies {
    implementation(libs.dependency.springcloud.gateway)
    implementation(libs.dependency.springcloud.config)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
