import org.gradle.api.initialization.resolve.RepositoriesMode

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "pokerbaby"

include("poker-engine-core", "poker-engine-json", "poker-engine-demo", "poker-engine-web")
