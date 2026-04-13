# Swift Multiplatform Gradle Plugin — Proposal

## Problem Statement

Building a Swift SDK that targets both iOS and Android today requires two separate build systems stitched together with workarounds:

- **Two `Package.swift` files** — one for iOS (SPM/XCFramework), one for Android (cross-compilation via swift-java)
- **A symlink** (`android/Sources -> ../Sources`) because SPM won't let a package reference sources outside its root directory
- **Separate CI jobs** for each platform — different toolchains, different publishing steps
- **No unified version management** — version lives in `gradle.properties` for Android, git tags for iOS
- **Shell scripts** for XCFramework generation that live outside the build system

Kotlin Multiplatform solved this years ago with a single Gradle plugin that produces both Android AARs and iOS XCFrameworks from one source tree, one build config, one command. Swift has no equivalent.

## Proposed Solution

A custom **Gradle plugin** (`com.dallaslabs.swift-multiplatform`) that unifies the entire build pipeline for Swift cross-platform SDKs:

```kotlin
plugins {
    id("com.dallaslabs.swift-multiplatform") version "1.0.0"
}

swiftMultiplatform {
    moduleName = "SwiftAndroidSDK"
    sourcesDir = "Sources/SwiftAndroidSDK"
    version = "1.1.7"

    android {
        abis("arm64-v8a", "x86_64")
        swiftSdk("swift-6.3-RELEASE_android")
        minSdk(28)
        jextract(enabled = true)
        namespace("com.dallaslabs.sdk")
    }

    ios {
        targets("ios-arm64", "ios-simulator-arm64", "ios-simulator-x86_64")
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
            registryUrl = "http://34.60.86.141:3000/api/packages/dallaslabs-sdk/swift"
            scope = "dallaslabs-sdk"
            packageName = "swift-android-sdk"
        }
    }
}
```

One `./gradlew publishAll` produces and publishes everything.

## What the Plugin Does

### Build Tasks

| Task | What it does |
|------|-------------|
| `swiftResolve` | Runs `swift package resolve` to fetch SPM dependencies |
| `buildSwiftAndroid` | Cross-compiles Swift for each Android ABI via `swift build --swift-sdk <triple>` |
| `buildSwiftAndroid_arm64` | Single-ABI variant |
| `buildSwiftAndroid_x86_64` | Single-ABI variant |
| `copyJniLibs` | Bundles `.so` files + Swift runtime into `jniLibs/` |
| `assembleAndroidAAR` | Produces the Android AAR with JExtract-generated Java sources |
| `buildIosDevice` | Builds Swift for `ios-arm64` (physical device) |
| `buildIosSimulator` | Builds Swift for `ios-simulator-arm64` + `ios-simulator-x86_64` |
| `assembleXCFramework` | Runs `xcodebuild -create-xcframework` from the platform archives |
| `buildAll` | Depends on `assembleAndroidAAR` + `assembleXCFramework` |

### Publishing Tasks

| Task | What it does |
|------|-------------|
| `publishAndroid` | Uploads AAR + POM to GCS Maven |
| `publishIosGcs` | Zips XCFramework, computes checksum, uploads to GCS |
| `publishIosGitea` | Creates source archive with `binaryTarget` Package.swift, uploads to Gitea Swift Registry with metadata |
| `publishAll` | Depends on `publishAndroid` + `publishIosGcs` + `publishIosGitea` |

### Utility Tasks

| Task | What it does |
|------|-------------|
| `swiftTest` | Runs `swift test` (host platform tests) |
| `swiftFormat` | Runs `swift-format` on sources (optional) |
| `printVersion` | Prints the current version |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Gradle Plugin                           │
│  com.dallaslabs.swift-multiplatform                      │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  SwiftMultiplatformPlugin.kt                             │
│    ├── Applies android-library plugin                    │
│    ├── Creates swiftMultiplatform {} extension            │
│    ├── Registers all tasks lazily                        │
│    └── Wires task dependencies                           │
│                                                          │
│  SwiftMultiplatformExtension.kt                          │
│    ├── Top-level config (moduleName, sourcesDir, version)│
│    ├── AndroidConfig (abis, swiftSdk, minSdk, jextract) │
│    ├── IosConfig (targets, minimumDeployment, framework) │
│    └── PublishingConfig (maven, gitea)                   │
│                                                          │
│  Tasks:                                                  │
│    ├── SwiftResolveTask.kt      (swift package resolve)  │
│    ├── SwiftBuildAndroidTask.kt (per-ABI cross-compile)  │
│    ├── CopyJniLibsTask.kt      (bundle .so + runtime)   │
│    ├── SwiftBuildIosTask.kt     (xcodebuild archive)     │
│    ├── AssembleXCFrameworkTask.kt (create-xcframework)   │
│    ├── PublishMavenTask.kt      (GCS Maven upload)       │
│    ├── PublishGcsTask.kt        (XCFramework → GCS)      │
│    ├── PublishGiteaTask.kt      (source archive → Gitea) │
│    └── SwiftTestTask.kt        (swift test)              │
│                                                          │
└─────────────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────┐
│               Consumer SDK Project                       │
│  SwiftAndroidSdk/                                        │
├─────────────────────────────────────────────────────────┤
│  Sources/SwiftAndroidSDK/    ← single source tree        │
│  Tests/SwiftAndroidSDKTests/                             │
│  Package.swift               ← dependency manifest only  │
│  build.gradle.kts            ← applies the plugin        │
│  settings.gradle.kts                                     │
│  gradle.properties           ← version + credentials     │
│                                                          │
│  NO android/ subdirectory                                │
│  NO symlink                                              │
│  NO second Package.swift                                 │
│  NO shell scripts                                        │
└─────────────────────────────────────────────────────────┘
```

## What Gets Eliminated in the SDK

| Before (current) | After (with plugin) |
|---|---|
| `android/Package.swift` | Removed |
| `android/Sources -> ../Sources` (symlink) | Removed |
| `android/build.gradle` (200+ lines) | `build.gradle.kts` (~30 lines applying plugin) |
| `android/settings.gradle` | Merged into root `settings.gradle.kts` |
| `scripts/build-xcframework.sh` | Removed (plugin task) |
| `scripts/publish-xcframework.gradle` | Removed (plugin task) |
| `scripts/publish-swiftkit-core.gradle` | Kept (swiftkit-core is external) |
| `.github/workflows/release.yml` (2 jobs) | 1 job: `./gradlew publishAll` |
| Version in `android/gradle.properties` | Version in root `gradle.properties` |

## How the SDK's `build.gradle.kts` Looks After

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

## How CI Simplifies

**Before** — `release.yml` with 2 jobs (build-aar, build-xcframework), each ~100 lines:

```yaml
jobs:
  build-aar:    # ~80 lines: checkout, JDK, NDK, swift-java resolve, bootstrap, build, publish
  build-xcframework:  # ~60 lines: checkout, xcodebuild, zip, checksum, GCS, Gitea
```

**After** — single job:

```yaml
jobs:
  publish:
    runs-on: [self-hosted, mac-studio]
    steps:
      - uses: actions/checkout@v4
      - name: Authenticate to GCP
        uses: google-github-actions/auth@v2
        with:
          credentials_json: ${{ secrets.GCS_SA_KEY_JSON }}
      - name: Publish all artifacts
        run: ./gradlew publishAll
        env:
          GITEA_TOKEN: ${{ secrets.GITEA_TOKEN }}
```

## Package.swift Role Change

The root `Package.swift` remains but its role changes:

**Before:** Build system entry point for iOS — SPM resolves deps AND builds the framework.

**After:** Dependency manifest only — SPM resolves deps (Swinject, swift-java), but Gradle orchestrates all builds via `swift build` and `xcodebuild`.

```swift
// Package.swift — dependency manifest, not a build system
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
            swiftSettings: [.swiftLanguageMode(.v5)],
            plugins: [
                .plugin(name: "JExtractSwiftPlugin", package: "swift-java"),
            ]
        ),
        .testTarget(name: "SwiftAndroidSDKTests", dependencies: ["SwiftAndroidSDK"]),
    ]
)
```

One Package.swift that works for both platforms. The `.dynamic` type is needed for Android `.so` generation. For the iOS XCFramework, the plugin uses `xcodebuild` which produces a static framework regardless of the SPM library type declaration.

## Plugin Publishing

The plugin itself is published to **GitHub Packages** under `erikg84/swift-multiplatform-gradle-plugin`:

```
com.dallaslabs:swift-multiplatform-gradle-plugin:1.0.0
```

Consumer SDKs add it in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://maven.pkg.github.com/erikg84/swift-multiplatform-gradle-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

## Technology Choices

| Choice | Rationale |
|--------|-----------|
| **Kotlin DSL** | Type-safe build scripts, IDE autocomplete for plugin DSL |
| **Composite build** | Independent testing, no buildSrc cache invalidation |
| **Lazy task registration** | Tasks only configured when needed — faster builds |
| **ExecOperations** | Gradle API for invoking swift/xcodebuild — proper output handling |
| **GitHub Packages** | Private plugin, matches existing infrastructure |
| **Property-based config** | `providers.gradleProperty()` — credentials never in source |

## Prior Art

| Project | What it does | What we learn from it |
|---------|-------------|----------------------|
| **kmp-framework-bundler** (prof18) | Gradle plugin that generates XCFrameworks from KMP projects | Task structure, DSL design, XCFramework assembly pattern |
| **multiplatform-swiftpackage** (luca992) | Generates XCFramework + Package.swift for SPM | SPM manifest generation, multi-platform target handling |
| **kotlin-gradle-plugin-template** (cortinico) | Template for Kotlin Gradle plugins | Project structure, CI, publishing setup |
| **swift-android-gradle** (Readdle) | Gradle plugin for Swift Android builds | Swift cross-compilation task patterns |
| **Official swift-android-examples** | Reference implementations | Gradle + Swift build integration patterns |

## Scope & Phases

### Phase 1: Core Plugin (MVP)
- Plugin structure with extension DSL
- Android build tasks (cross-compile, bundle .so, JExtract)
- iOS build tasks (xcodebuild archive, create-xcframework)
- `buildAll` task
- Unit tests with Gradle TestKit

### Phase 2: Publishing
- Maven publishing (GCS)
- GCS XCFramework upload
- Gitea Swift Registry upload
- `publishAll` task
- Checksum computation

### Phase 3: CI Integration
- Migrate SwiftAndroidSdk to the plugin (on `alt_build_system` branch)
- Single-job CI workflow
- Version management from gradle.properties
- Remove android/ subdirectory, symlink, shell scripts

### Phase 4: Polish
- Gradle build cache support
- Incremental build detection
- Error messages with actionable fixes
- Documentation + example project
- Publish plugin v1.0.0

## Success Criteria

1. `./gradlew buildAll` produces both AAR and XCFramework from a single source tree
2. `./gradlew publishAll` publishes to GCS Maven + GCS + Gitea in one command
3. SwiftAndroidSdk repo has zero symlinks, one Package.swift, one build.gradle.kts
4. CI workflow is a single job under 20 lines
5. Plugin is consumable from GitHub Packages by any new Swift cross-platform SDK

## License

Apache 2.0
