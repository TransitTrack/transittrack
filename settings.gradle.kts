pluginManagement {
    val gitPropertiesPluginVersion: String by settings
    val jibPluginVersion: String by settings
    val springVersion: String by settings
    val protobufPluginVersion: String by settings

    plugins {
        id("org.springframework.boot") version springVersion
        id("io.spring.dependency-management") version "1.1.7"

        id("com.google.cloud.tools.jib") version jibPluginVersion
        id("com.palantir.git-version") version "5.0.0"
        id("com.gorylenko.gradle-git-properties") version gitPropertiesPluginVersion

        id("com.google.protobuf") version protobufPluginVersion
        id("com.diffplug.spotless") version "8.9.0"
        id("com.github.andygoossens.gradle-modernizer-plugin") version "1.15.0"
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

rootProject.name = "transitclock"

include(":libs:api", ":libs:util", ":libs:core")
include(":libs:extensions:traccar")
include(":app")
