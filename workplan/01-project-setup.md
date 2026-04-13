# 01 — Project Setup

## Goal
Scaffold the Gradle plugin project with composite build structure, Kotlin DSL, and CI skeleton.

## Directory Structure

```
swift-multiplatform-gradle-plugin/
├── plugin/                              # The plugin itself (composite build)
│   ├── src/main/kotlin/
│   │   └── com/dallaslabs/gradle/swift/
│   │       ├── SwiftMultiplatformPlugin.kt       # Plugin entry point
│   │       ├── SwiftMultiplatformExtension.kt    # DSL root
│   │       ├── AndroidConfig.kt                  # android {} block
│   │       ├── IosConfig.kt                      # ios {} block
│   │       ├── PublishingConfig.kt                # publishing {} block
│   │       └── tasks/
│   │           ├── SwiftResolveTask.kt
│   │           ├── SwiftBuildAndroidTask.kt
│   │           ├── CopyJniLibsTask.kt
│   │           ├── SwiftBuildIosTask.kt
│   │           ├── AssembleXCFrameworkTask.kt
│   │           ├── PublishMavenTask.kt
│   │           ├── PublishGcsTask.kt
│   │           ├── PublishGiteaTask.kt
│   │           └── SwiftTestTask.kt
│   ├── src/test/kotlin/                          # Gradle TestKit tests
│   │   └── com/dallaslabs/gradle/swift/
│   │       └── SwiftMultiplatformPluginTest.kt
│   ├── build.gradle.kts
│   └── gradle.properties
├── example/                              # Example consumer project
│   ├── Sources/ExampleLib/
│   │   └── Example.swift
│   ├── Package.swift
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── settings.gradle.kts                   # Includes plugin/ as composite build
├── build.gradle.kts                      # Root (mostly empty)
├── gradle.properties
├── PROPOSAL.md
├── workplan/
└── .github/workflows/
    ├── ci.yml                            # Build + test on PR
    └── publish.yml                       # Publish to GitHub Packages on tag
```

## Files to Create

### `settings.gradle.kts` (root)
```kotlin
rootProject.name = "swift-multiplatform-gradle-plugin"
includeBuild("plugin")
```

### `plugin/build.gradle.kts`
```kotlin
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
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
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
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
```

### `plugin/gradle.properties`
```properties
VERSION=0.1.0-SNAPSHOT
```

### `gradle/libs.versions.toml`
```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
junit = "5.10.2"
```

## Steps

1. Initialize Gradle wrapper (8.11+)
2. Create root `settings.gradle.kts` with composite build include
3. Create `plugin/` directory with `build.gradle.kts`
4. Create empty `SwiftMultiplatformPlugin.kt` that applies android-library
5. Create empty extension classes
6. Verify `./gradlew :plugin:build` compiles
7. Create `.gitignore` (build dirs, .gradle, .idea)
8. Initial commit

## Verification

```bash
cd swift-multiplatform-gradle-plugin
./gradlew :plugin:build
# Should compile with zero errors
```
