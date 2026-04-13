# 07 — SDK Migration

## Goal
Migrate SwiftAndroidSdk (on `alt_build_system` branch) to use the plugin, eliminating the android/ subdirectory, symlink, and shell scripts.

## Current SDK Structure (before)

```
SwiftAndroidSdk/
├── Sources/SwiftAndroidSDK/           ← actual source
├── Tests/SwiftAndroidSDKTests/
├── Package.swift                      ← iOS entry point (platforms: iOS, macOS)
├── android/
│   ├── Package.swift                  ← Android entry point (platforms: macOS)
│   ├── Sources -> ../Sources          ← SYMLINK
│   ├── build.gradle                   ← 200+ lines
│   ├── settings.gradle
│   ├── gradle.properties
│   ├── gradlew / gradlew.bat
│   └── gradle/
├── scripts/
│   ├── build-xcframework.sh
│   ├── publish-xcframework.gradle
│   └── publish-swiftkit-core.gradle
├── .github/workflows/
│   ├── release.yml                    ← 2 jobs, ~180 lines
│   └── ci.yml
└── README.md
```

## Target SDK Structure (after)

```
SwiftAndroidSdk/
├── Sources/SwiftAndroidSDK/           ← same source, untouched
├── Tests/SwiftAndroidSDKTests/
├── Package.swift                      ← unified (both platforms)
├── build.gradle.kts                   ← ~30 lines, applies plugin
├── settings.gradle.kts                ← plugin resolution
├── gradle.properties                  ← version + credentials
├── gradle/
│   └── wrapper/
├── gradlew / gradlew.bat
├── scripts/
│   └── publish-swiftkit-core.gradle   ← kept (external dep)
├── .github/workflows/
│   ├── release.yml                    ← 1 job, ~15 lines
│   └── ci.yml
└── README.md
```

## What Gets Removed

- `android/` directory (entire thing)
  - `android/Package.swift`
  - `android/Sources` (symlink)
  - `android/build.gradle`
  - `android/settings.gradle`
  - `android/gradle.properties`
  - `android/gradle/` + wrapper
- `scripts/build-xcframework.sh`
- `scripts/publish-xcframework.gradle`

## What Gets Modified

### Package.swift (unified)

Merge the two Package.swift files into one that works for both platforms:

```swift
// swift-tools-version: 6.2
import PackageDescription

let package = Package(
    name: "SwiftAndroidSDK",
    platforms: [.iOS(.v15), .macOS(.v15)],
    products: [
        .library(name: "SwiftAndroidSDK", type: .dynamic, targets: ["SwiftAndroidSDK"]),
    ],
    dependencies: [
        .package(url: "https://github.com/Swinject/Swinject.git", from: "2.10.0"),
        .package(url: "https://github.com/swiftlang/swift-java.git", from: "0.1.2"),
    ],
    targets: [
        .target(
            name: "SwiftAndroidSDK",
            dependencies: [
                .product(name: "Swinject", package: "Swinject"),
                .product(name: "SwiftJava", package: "swift-java"),
            ],
            exclude: ["swift-java.config", "Container/TMDBContainerTestHooks.swift"],
            swiftSettings: [.swiftLanguageMode(.v5)],
            plugins: [
                .plugin(name: "JExtractSwiftPlugin", package: "swift-java"),
            ]
        ),
        .testTarget(
            name: "SwiftAndroidSDKTests",
            dependencies: ["SwiftAndroidSDK"]
        ),
    ]
)
```

Note: swift-java and JExtract are included for both platforms. On iOS, JExtract runs but its output (Java sources) is simply unused. The `.dynamic` type produces `.so` on Android; `xcodebuild` still produces a proper static framework for the XCFramework regardless.

### build.gradle.kts (new, replaces android/build.gradle)

```kotlin
plugins {
    id("com.dallaslabs.swift-multiplatform") version "1.0.0"
}

swiftMultiplatform {
    moduleName = "SwiftAndroidSDK"
    sourcesDir = "Sources/SwiftAndroidSDK"
    version = providers.gradleProperty("VERSION_NAME")

    android {
        abis("arm64-v8a", "x86_64")
        swiftSdk("swift-6.3-RELEASE_android")
        minSdk(28)
        compileSdk(36)
        jextract(enabled = true)
        namespace("com.dallaslabs.sdk.swiftandroidsdk")
        excludeFromSwift("Container/TMDBContainerTestHooks.swift", "swift-java.config")
    }

    ios {
        targets("ios-arm64", "ios-simulator-arm64")
        minimumDeployment("15.0")
        frameworkName("SwiftAndroidSDK")
    }

    publishing {
        maven {
            groupId = "com.dallaslabs.sdk"
            artifactId = "swift-android-sdk"
            repository = "https://storage.googleapis.com/dallaslabs-sdk-artifacts/maven"
        }
        gitea {
            registryUrl = providers.gradleProperty("GITEA_URL")
            token = providers.gradleProperty("GITEA_TOKEN")
            scope = "dallaslabs-sdk"
            packageName = "swift-android-sdk"
        }
    }
}
```

### settings.gradle.kts (new)

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            name = "DallasLabsPlugins"
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

rootProject.name = "SwiftAndroidSDK"
```

### gradle.properties

```properties
VERSION_NAME=1.2.0
GROUP_ID=com.dallaslabs.sdk
ARTIFACT_ID=swift-android-sdk

# Gitea (CI: from secrets; local: set here or in ~/.gradle/gradle.properties)
# GITEA_URL=http://34.60.86.141:3000
# GITEA_TOKEN=your_token

# GitHub Packages (for plugin resolution)
# gpr.user=erikg84
# gpr.key=ghp_xxxxx

org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
```

### release.yml (simplified)

```yaml
name: Release
on:
  push:
    tags: ['v*']

permissions:
  contents: read
  packages: read

jobs:
  publish:
    runs-on: [self-hosted, mac-studio]
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Configure JDK 17
        run: |
          echo "JAVA_HOME=$(/usr/libexec/java_home -v 17)" >> "$GITHUB_ENV"

      - name: Authenticate to GCP
        env:
          GCS_SA_KEY_JSON: ${{ secrets.GCS_SA_KEY_JSON }}
        run: |
          echo "$GCS_SA_KEY_JSON" > "$RUNNER_TEMP/gcs-key.json"
          echo "GOOGLE_APPLICATION_CREDENTIALS=$RUNNER_TEMP/gcs-key.json" >> "$GITHUB_ENV"

      - name: Publish all artifacts
        run: ./gradlew publishAll --no-daemon
        env:
          GITEA_URL: ${{ secrets.GITEA_URL }}
          GITEA_TOKEN: ${{ secrets.GITEA_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Migration Steps

1. Ensure plugin is published (at least to Maven Local)
2. Create `build.gradle.kts` and `settings.gradle.kts` at SDK root
3. Create `gradle.properties` with version + config
4. Initialize Gradle wrapper at root: `gradle wrapper --gradle-version 8.11.1`
5. Merge the two Package.swift files into one
6. Delete `android/` directory entirely (including symlink)
7. Delete `scripts/build-xcframework.sh` and `scripts/publish-xcframework.gradle`
8. Update `.github/workflows/release.yml` to single job
9. Run `./gradlew buildAll` — verify both AAR and XCFramework
10. Run `./gradlew publishAll` — verify GCS + Gitea
11. Test iOS client resolution of new version
12. Test Android client resolution of new version

## Rollback

The `alt_build_system` branch isolates all changes. If anything fails:
- `git checkout main` — original structure is untouched
- Plugin development continues independently
- Merge only when fully validated

## Verification

```bash
# On alt_build_system branch
./gradlew tasks --group swift
# Should show: swiftResolve, buildSwiftAndroid, assembleXCFramework, publishAll, etc.

./gradlew buildAll
# Should produce AAR + XCFramework

./gradlew publishAll
# Should publish to GCS Maven + GCS + Gitea

# Verify no symlinks, no android/ dir
find . -type l          # should return nothing
ls android/ 2>&1        # should say "No such file or directory"
```
