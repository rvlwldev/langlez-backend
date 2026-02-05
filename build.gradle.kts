import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun
import org.springframework.boot.gradle.tasks.bundling.BootJar

group = "com.langlez"
version = "0.0.1-SNAPSHOT"
description = "langlez-backend"

data class PluginId(
    val kotlinJvm: String,
    val springDependency: String,
    val springboot: String,
)

val pluginId =
    PluginId(
        kotlinJvm = libs.plugins.kotlin.jvm.get().pluginId,
        springDependency = libs.plugins.spring.dependency.management.get().pluginId,
        springboot = libs.plugins.springboot.get().pluginId,
    )

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.springboot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "com.langlez"
    version = "0.0.1-SNAPSHOT"

    apply(plugin = pluginId.kotlinJvm)
    apply(plugin = pluginId.springDependency)

    repositories {
        mavenCentral()
    }
}

subprojects {
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            javaParameters.set(true)
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<BootRun> {
        val args =
            System
                .getProperties()
                .filter { it.key.toString().startsWith("spring.") }
                .mapKeys { it.key.toString() }
        systemProperties(args)
    }

    plugins.withId(pluginId.springboot) {
        if (project.path != ":app:api" && project.name != "api") {
            tasks.named<BootJar>("bootJar") { enabled = false }
            tasks.named<Jar>("jar") { enabled = true }
        } else {
             tasks.named<Jar>("jar") { enabled = true }
        }
    }

    dependencies {
        "testImplementation"(rootProject.libs.bundles.test.kotest)
        "testImplementation"(rootProject.libs.test.mockk)
    }
}

dependencies {
    implementation(kotlin("stdlib"))
}
