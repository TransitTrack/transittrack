pluginManagement {
    val kotlinVersion: String by settings
    val gitPropertiesPluginVersion: String by settings
    val jibPluginVersion: String by settings
    val springVersion: String by settings
    val protobufPluginVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion

        id("org.springframework.boot") version springVersion
        id("io.spring.dependency-management") version "1.1.7"

        id("com.google.cloud.tools.jib") version jibPluginVersion
        id("com.palantir.git-version") version "3.1.0"
        id("com.gorylenko.gradle-git-properties") version gitPropertiesPluginVersion

        id("com.google.protobuf") version protobufPluginVersion
        id("com.diffplug.spotless") version "7.2.1"
        id("com.github.andygoossens.gradle-modernizer-plugin") version "1.10.0"
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

rootProject.name = "transitclock"

include(":libs:util", ":libs:core")
include(":libs:extensions:api", ":libs:extensions:traccar")
include(":app")
