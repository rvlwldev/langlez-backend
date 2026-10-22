rootProject.name = "langlez-api-master"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

fun includeModules(parentDir: String) {
    val dir = File(rootDir, parentDir)
    if (dir.exists() && dir.isDirectory) {
        dir.listFiles()
            ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
            ?.forEach { include("$parentDir:${it.name}") }
    }
}

include("gateway")
include("config-server")
include("core")
include("common")
includeModules("infra")
includeModules("module")