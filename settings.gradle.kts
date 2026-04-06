pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "transactions-system"

include("contracts", "platform-core", "data-generator")
