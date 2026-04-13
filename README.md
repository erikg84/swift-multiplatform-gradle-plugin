# Swift Multiplatform Gradle Plugin

A Gradle plugin that unifies **Android AAR** and **iOS XCFramework** generation from a single Swift source tree. The Swift equivalent of what the Kotlin Multiplatform Gradle plugin does for Kotlin.

## Problem

Building a Swift SDK for both iOS and Android today requires:
- Two `Package.swift` files (one per platform)
- A symlink to share sources between them
- Separate CI jobs for Android and iOS
- Shell scripts for XCFramework generation
- Manual version coordination

This plugin replaces all of that with one `build.gradle.kts`.

## Usage

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/erikg84/swift-multiplatform-gradle-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
```

### `build.gradle.kts`

```kotlin
plugins {
    id("com.dallaslabs.swift-multiplatform") version "0.1.0"
}

swiftMultiplatform {
    moduleName = "MySwiftSDK"
    sourcesDir = "Sources/MySwiftSDK"
    version = "1.0.0"

    android {
        abis("arm64-v8a", "x86_64")
        swiftSdk("swift-6.3-RELEASE_android.artifactbundle")
        minSdk(28)
        compileSdk(36)
        jextract(enabled = true)
        namespace("com.example.myswiftsdk")
    }

    ios {
        targets("ios-arm64", "ios-simulator-arm64")
        minimumDeployment("15.0")
        frameworkName("MySwiftSDK")
    }

    publishing {
        maven {
            groupId = "com.example"
            artifactId = "my-swift-sdk"
            repository = "https://storage.googleapis.com/your-bucket/maven"
        }
        gitea {
            registryUrl = "http://your-gitea:3000"
            token = providers.gradleProperty("GITEA_TOKEN")
            scope = "your-scope"
            packageName = "my-swift-sdk"
        }
    }
}
```

## Tasks

### Build

| Task | Description |
|------|-------------|
| `swiftResolve` | Runs `swift package resolve` to fetch SPM dependencies |
| `bootstrapSwiftkitCore` | Bootstraps swiftkit-core into Maven Local (required by swift-java) |
| `buildSwiftAndroid` | Cross-compiles Swift for all configured Android ABIs |
| `buildSwiftAndroid_arm64v8a` | Cross-compiles for arm64-v8a only |
| `copyJniLibs` | Bundles `.so` files + Swift runtime into jniLibs |
| `buildIosDevice` | Archives framework for iOS device (arm64) |
| `buildIosSimulator` | Archives framework for iOS Simulator |
| `assembleXCFramework` | Creates XCFramework from device + simulator archives |
| `zipXCFramework` | Zips XCFramework and computes SHA-256 checksum |
| `buildAll` | Builds both Android AAR and iOS XCFramework |
| `swiftTest` | Runs `swift test` on the host platform |

### Publishing

| Task | Description |
|------|-------------|
| `publishAndroid` | Publishes Android AAR to Maven repository (GCS) |
| `publishIosGcs` | Uploads XCFramework zip to Google Cloud Storage |
| `publishIosGitea` | Publishes source archive to Gitea Swift Package Registry |
| `publishAll` | Runs all three publishing tasks |

## What It Replaces

| Before | After |
|--------|-------|
| 2 Package.swift files | 1 Package.swift (dependency manifest only) |
| Symlink `android/Sources -> ../Sources` | Gone |
| `android/build.gradle` (200+ lines) | `build.gradle.kts` (~30 lines) |
| `scripts/build-xcframework.sh` | `assembleXCFramework` task |
| 2 CI jobs (~180 lines) | 1 CI job (~15 lines): `./gradlew publishAll` |

## Prerequisites

- **JDK 17** (for Gradle + Android builds)
- **Swift 6.3+** with Swift SDK for Android installed
- **Xcode 16+** (for `xcodebuild` — iOS XCFramework generation)
- **swiftly** (Swift version manager, used for cross-compilation)
- **gcloud** CLI (for GCS publishing, optional)

## DSL Reference

### `swiftMultiplatform`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `moduleName` | `String` | required | Swift module name |
| `sourcesDir` | `String` | required | Path to Swift sources relative to project root |
| `version` | `String` | required | SDK version |

### `android`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `abis` | `List<String>` | `["arm64-v8a", "x86_64"]` | Android ABIs to cross-compile |
| `swiftSdk` | `String` | `"swift-6.3-RELEASE_android.artifactbundle"` | Swift SDK bundle name |
| `swiftVersion` | `String` | `"6.3"` | Swift version for swiftly |
| `minSdk` | `Int` | `28` | Android minSdk |
| `compileSdk` | `Int` | `36` | Android compileSdk |
| `namespace` | `String` | `"com.dallaslabs.sdk"` | Android library namespace |
| `jextractEnabled` | `Boolean` | `true` | Enable JExtract Java binding generation |
| `excludeFromSwift` | `List<String>` | `[]` | Files to exclude from Android builds |
| `republishSwiftkitCore` | `Boolean` | `true` | Auto-bootstrap swiftkit-core to Maven Local |

### `ios`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `targets` | `List<String>` | `["ios-arm64", "ios-simulator-arm64"]` | iOS build targets |
| `minimumDeployment` | `String` | `"15.0"` | Minimum iOS deployment target |
| `frameworkName` | `String` | `moduleName` | XCFramework name |
| `buildScript` | `String` | not set | Custom script for complex XCFramework builds |

### `publishing.maven`

| Property | Type | Description |
|----------|------|-------------|
| `groupId` | `String` | Maven group ID |
| `artifactId` | `String` | Maven artifact ID |
| `repository` | `String` | Maven repository URL |

### `publishing.gitea`

| Property | Type | Description |
|----------|------|-------------|
| `registryUrl` | `String` | Gitea server URL |
| `token` | `String` | Gitea API token |
| `scope` | `String` | Swift Package Registry scope |
| `packageName` | `String` | Package name within scope |
| `authorName` | `String` | Author name for registry metadata (default: "Dallas Labs") |

## Custom XCFramework Build Script

For SDKs with complex XCFramework requirements (manual `.o` reassembly, custom framework structure), set `ios.buildScript`:

```kotlin
ios {
    buildScript = "scripts/build-xcframework.sh"  // receives output dir as $1
}
```

The plugin delegates to this script instead of using `xcodebuild` directly. The script must produce a valid `.xcframework` at `<outputDir>/<frameworkName>.xcframework`.

## Development

```bash
# Build
./gradlew :plugin:build

# Run tests
./gradlew :plugin:test

# Publish to Maven Local (for local development)
./gradlew :plugin:publishToMavenLocal

# Publish to GitHub Packages (requires gpr.user + gpr.key)
./gradlew :plugin:publishAllPublicationsToGitHubPackagesRepository
```

## License

Apache 2.0
