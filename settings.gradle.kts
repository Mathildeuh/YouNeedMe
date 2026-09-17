rootProject.name = "YouNeedMe"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// gradle/libs.versions.toml is picked up automatically as the "libs" catalog by convention.

include(":api")
include(":core")
include(":expansions:expansion-api")
include(":expansions:expansion-placeholders")
