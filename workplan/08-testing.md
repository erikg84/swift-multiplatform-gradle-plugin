# 08 — Testing

## Goal
Comprehensive testing of the plugin using Gradle TestKit and integration tests.

## Test Layers

### 1. Unit Tests (Gradle TestKit)

Gradle TestKit runs a real Gradle build in a temporary directory with the plugin applied. Tests verify task behavior without needing Swift/Xcode installed.

```kotlin
class SwiftMultiplatformPluginTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `plugin applies without error`() {
        writeSettings()
        writeBuildFile("""
            plugins {
                id("com.dallaslabs.swift-multiplatform")
            }
            swiftMultiplatform {
                moduleName = "TestModule"
                sourcesDir = "Sources/TestModule"
                version = "1.0.0"
            }
        """)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--group", "swift")
            .build()

        assertThat(result.output).contains("swiftResolve")
        assertThat(result.output).contains("buildSwiftAndroid")
        assertThat(result.output).contains("assembleXCFramework")
        assertThat(result.output).contains("publishAll")
    }

    @Test
    fun `extension DSL parses correctly`() {
        writeSettings()
        writeBuildFile("""
            plugins {
                id("com.dallaslabs.swift-multiplatform")
            }
            swiftMultiplatform {
                moduleName = "MySDK"
                sourcesDir = "Sources/MySDK"
                version = "2.0.0"
                android {
                    abis("arm64-v8a")
                    minSdk(26)
                }
                ios {
                    minimumDeployment("16.0")
                }
            }
            tasks.register("printConfig") {
                doLast {
                    val ext = project.extensions.getByType<SwiftMultiplatformExtension>()
                    println("module=${ext.moduleName.get()}")
                    println("abis=${ext.android.abis.get()}")
                    println("minSdk=${ext.android.minSdk.get()}")
                    println("iosDeployment=${ext.ios.minimumDeployment.get()}")
                }
            }
        """)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("printConfig")
            .build()

        assertThat(result.output).contains("module=MySDK")
        assertThat(result.output).contains("abis=[arm64-v8a]")
        assertThat(result.output).contains("minSdk=26")
        assertThat(result.output).contains("iosDeployment=16.0")
    }

    @Test
    fun `fails fast when moduleName not set`() {
        writeSettings()
        writeBuildFile("""
            plugins {
                id("com.dallaslabs.swift-multiplatform")
            }
            swiftMultiplatform {
                sourcesDir = "Sources/Test"
                version = "1.0.0"
            }
        """)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail()

        assertThat(result.output).contains("moduleName must be set")
    }

    @Test
    fun `ABI to triple mapping is correct`() {
        assertEquals(
            "aarch64-unknown-linux-android28",
            tripleForAbi("arm64-v8a", 28)
        )
        assertEquals(
            "x86_64-unknown-linux-android28",
            tripleForAbi("x86_64", 28)
        )
    }
}
```

### 2. Integration Tests (require Swift + Xcode)

These run on a machine with the full toolchain. Slower, run in CI only.

```kotlin
@Tag("integration")
class SwiftBuildIntegrationTest {

    @Test
    fun `builds Swift for Android arm64`() {
        // Uses a minimal Swift package in test fixtures
        val result = GradleRunner.create()
            .withProjectDir(fixtureProject("minimal-swift-lib"))
            .withPluginClasspath()
            .withArguments("buildSwiftAndroid_arm64v8a")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":buildSwiftAndroid_arm64v8a")?.outcome)
        assertTrue(File(fixtureProject, ".build/aarch64-unknown-linux-android28/debug").exists())
    }

    @Test
    fun `assembles XCFramework`() {
        val result = GradleRunner.create()
            .withProjectDir(fixtureProject("minimal-swift-lib"))
            .withPluginClasspath()
            .withArguments("assembleXCFramework")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleXCFramework")?.outcome)
        assertTrue(File(fixtureProject, "build/xcframeworks/MinimalLib.xcframework/Info.plist").exists())
    }
}
```

### 3. Test Fixtures

Minimal Swift package for testing:

```
plugin/src/test/resources/fixtures/minimal-swift-lib/
├── Sources/MinimalLib/
│   └── Greeting.swift          // public func greet() -> String { "Hello" }
├── Package.swift
├── build.gradle.kts            // applies our plugin
└── settings.gradle.kts
```

## Test Matrix

| Test | Layer | Requires Toolchain | CI |
|------|-------|-------------------|-----|
| Plugin applies | Unit | No | Always |
| DSL parsing | Unit | No | Always |
| Validation errors | Unit | No | Always |
| ABI mapping | Unit | No | Always |
| Task registration | Unit | No | Always |
| Android cross-compile | Integration | Swift SDK for Android | Mac runner |
| XCFramework assembly | Integration | Xcode | Mac runner |
| Publish to GCS | Integration | gcloud + credentials | Mac runner |
| Publish to Gitea | Integration | Gitea access | Mac runner |

## CI Test Configuration

```yaml
# .github/workflows/ci.yml
jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: ./gradlew :plugin:test

  integration-tests:
    runs-on: [self-hosted, mac-studio]
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew :plugin:test -Pinclude.integration=true
```

## Steps

1. Set up Gradle TestKit in `plugin/build.gradle.kts`
2. Create test fixtures (minimal Swift package)
3. Write unit tests for plugin application, DSL parsing, validation
4. Write unit tests for ABI mapping, toolchain discovery
5. Write integration tests for Android cross-compilation
6. Write integration tests for XCFramework assembly
7. Write integration tests for publishing (optional, manual trigger)
8. Configure CI to run unit tests on every PR, integration on Mac runner
