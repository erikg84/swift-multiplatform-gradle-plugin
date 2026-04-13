plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

group = "com.dallaslabs"
version = providers.gradleProperty("VERSION").getOrElse("0.1.0-SNAPSHOT")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

gradlePlugin {
    plugins {
        create("swiftMultiplatform") {
            id = "com.dallaslabs.swift-multiplatform"
            implementationClass = "com.dallaslabs.gradle.swift.SwiftMultiplatformPlugin"
            displayName = "Swift Multiplatform"
            description = "Unified Android AAR + iOS XCFramework builds from a single Swift source tree"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/erikg84/swift-multiplatform-gradle-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
