pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "fillit_android_compose"
include(":app")

// ✅ Now you can add toolchain auto-download (foojay resolver) here:
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
