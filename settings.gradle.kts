pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OfflineGameStudio"

include(":app")

include(":core:common")
include(":core:models")
include(":core:database")
include(":core:datastore")
include(":core:filesystem")
include(":core:ui")
include(":core:navigation")

include(":ai:runtime")
include(":ai:prompting")
include(":ai:agent")

include(":feature:chat")
include(":feature:projects")
include(":feature:editor")
include(":feature:preview")
include(":feature:terminal")
include(":feature:models-manager")
include(":feature:settings")
