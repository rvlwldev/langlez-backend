rootProject.name = "langlez-api"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

include("app")
include("share:auth")
include("share:kafka")
include("share:redis")
include("share:mongodb")
include("share:rdb")
include("share:jackson")
include("share:logger")
