rootProject.name = "langlez-backend-server"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

include(
    "app:api",
    ":common:exception",
    ":common:jackson",
    ":common:observability",
    ":common:swagger",
    ":common:i18n",
    ":common:security",
    ":common:logger",
    "infra:kafka",
    "infra:redis",
    "infra:mysql",
    "infra:mongo",
    "infra:files",
    "module:member",
    "module:auth",
)
