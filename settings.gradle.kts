pluginManagement {
  repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    mavenCentral()
  }
}

rootProject.name = "Terbian"

include(":app")
