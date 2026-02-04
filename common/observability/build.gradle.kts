dependencies {
    api(libs.dependency.springboot.starter)

    // Observability (Metrics & Tracing)
    api(libs.dependency.springboot.actuator)
    api(libs.dependency.micrometer.prometheus)
    api(libs.dependency.micrometer.tracing.brave)

    // JSON Logging
    api(libs.dependency.logstash.encoder)

    // SQL Monitoring (P6Spy)
    api(libs.dependency.p6spy.starter)

    implementation(kotlin("reflect"))
}
