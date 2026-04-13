# 02 — Extension DSL

## Goal
Define the `swiftMultiplatform {}` DSL that consumers use to configure their Swift cross-platform SDK builds.

## Extension Hierarchy

```
swiftMultiplatform {
    moduleName = "SwiftAndroidSDK"        // Swift module name
    sourcesDir = "Sources/SwiftAndroidSDK" // relative to project root
    version = "1.1.7"                      // or providers.gradleProperty("VERSION_NAME")

    android {                              // AndroidConfig
        abis("arm64-v8a", "x86_64")       // target ABIs
        swiftSdk("swift-6.3-RELEASE_android")
        minSdk(28)
        compileSdk(36)
        namespace("com.dallaslabs.sdk")
        jextract(enabled = true)
        excludeFromSwift("file1.swift", "file2.config")  // files JExtract can't handle
        swiftSettings {                    // additional swift build flags
            languageMode("5")
        }
    }

    ios {                                  // IosConfig
        targets("ios-arm64", "ios-simulator-arm64", "ios-simulator-x86_64")
        minimumDeployment("15.0")
        frameworkName("SwiftAndroidSDK")   // defaults to moduleName
    }

    publishing {                           // PublishingConfig
        maven {                            // MavenConfig
            groupId = "com.dallaslabs.sdk"
            artifactId = "swift-android-sdk"
            repository = "gs://dallaslabs-sdk-artifacts/maven"
        }
        gitea {                            // GiteaConfig
            registryUrl = "http://34.60.86.141:3000/api/packages/dallaslabs-sdk/swift"
            token = providers.gradleProperty("GITEA_TOKEN")
            scope = "dallaslabs-sdk"
            packageName = "swift-android-sdk"
        }
    }
}
```

## Kotlin Classes

### SwiftMultiplatformExtension.kt
```kotlin
abstract class SwiftMultiplatformExtension @Inject constructor(
    objects: ObjectFactory
) {
    abstract val moduleName: Property<String>
    abstract val sourcesDir: Property<String>
    abstract val version: Property<String>

    val android: AndroidConfig = objects.newInstance(AndroidConfig::class.java)
    val ios: IosConfig = objects.newInstance(IosConfig::class.java)
    val publishing: PublishingConfig = objects.newInstance(PublishingConfig::class.java)

    fun android(action: Action<AndroidConfig>) = action.execute(android)
    fun ios(action: Action<IosConfig>) = action.execute(ios)
    fun publishing(action: Action<PublishingConfig>) = action.execute(publishing)
}
```

### AndroidConfig.kt
```kotlin
abstract class AndroidConfig @Inject constructor(
    objects: ObjectFactory
) {
    abstract val abis: ListProperty<String>
    abstract val swiftSdk: Property<String>
    abstract val minSdk: Property<Int>
    abstract val compileSdk: Property<Int>
    abstract val namespace: Property<String>
    abstract val jextractEnabled: Property<Boolean>
    abstract val excludeFromSwift: ListProperty<String>

    init {
        abis.convention(listOf("arm64-v8a", "x86_64"))
        swiftSdk.convention("swift-6.3-RELEASE_android")
        minSdk.convention(28)
        compileSdk.convention(36)
        jextractEnabled.convention(true)
    }

    fun abis(vararg values: String) = abis.set(values.toList())
    fun jextract(enabled: Boolean) = jextractEnabled.set(enabled)
    fun excludeFromSwift(vararg files: String) = excludeFromSwift.set(files.toList())
}
```

### IosConfig.kt
```kotlin
abstract class IosConfig @Inject constructor(
    objects: ObjectFactory
) {
    abstract val targets: ListProperty<String>
    abstract val minimumDeployment: Property<String>
    abstract val frameworkName: Property<String>

    init {
        targets.convention(listOf("ios-arm64", "ios-simulator-arm64"))
        minimumDeployment.convention("15.0")
    }

    fun targets(vararg values: String) = targets.set(values.toList())
}
```

### PublishingConfig.kt
```kotlin
abstract class PublishingConfig @Inject constructor(
    objects: ObjectFactory
) {
    val maven: MavenConfig = objects.newInstance(MavenConfig::class.java)
    val gitea: GiteaConfig = objects.newInstance(GiteaConfig::class.java)

    fun maven(action: Action<MavenConfig>) = action.execute(maven)
    fun gitea(action: Action<GiteaConfig>) = action.execute(gitea)
}

abstract class MavenConfig {
    abstract val groupId: Property<String>
    abstract val artifactId: Property<String>
    abstract val repository: Property<String>
}

abstract class GiteaConfig {
    abstract val registryUrl: Property<String>
    abstract val token: Property<String>
    abstract val scope: Property<String>
    abstract val packageName: Property<String>
}
```

## Conventions & Defaults

| Property | Default | Can Override |
|----------|---------|-------------|
| `android.abis` | `["arm64-v8a", "x86_64"]` | Yes |
| `android.swiftSdk` | `"swift-6.3-RELEASE_android"` | Yes |
| `android.minSdk` | `28` | Yes |
| `android.compileSdk` | `36` | Yes |
| `android.jextractEnabled` | `true` | Yes |
| `ios.targets` | `["ios-arm64", "ios-simulator-arm64"]` | Yes |
| `ios.minimumDeployment` | `"15.0"` | Yes |
| `ios.frameworkName` | Falls back to `moduleName` | Yes |

## Validation

The plugin should validate at configuration time:
- `moduleName` is set (fail fast)
- `sourcesDir` points to an existing directory
- At least one Android ABI or iOS target is configured
- Publishing config is complete if any publish task is invoked

## Steps

1. Create all extension classes with abstract properties
2. Register extension in SwiftMultiplatformPlugin.apply()
3. Add convention defaults
4. Add validation logic (afterEvaluate or task-time)
5. Write unit tests verifying DSL parsing
