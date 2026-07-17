    pluginManagement {
        repositories {
            google {
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            mavenCentral()
            gradlePluginPortal()
        }
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
            // Mapbox Maven repository
            maven {
                url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
                authentication {
                    create<BasicAuthentication>("basic")
                }
                credentials {
                    // Do not change the username below.
                    // This should always be `mapbox` (not your username).
                    username = "mapbox"
                    // Use the secret token you stored in gradle.properties as the password
                    password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").get()
                }
            }

        }
    }

    rootProject.name = "Thesis Application"
    include(":app")
