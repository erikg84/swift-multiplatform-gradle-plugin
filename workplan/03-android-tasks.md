# 03 — Android Build Tasks

## Goal
Implement Gradle tasks that cross-compile Swift for Android, run JExtract, bundle .so files + Swift runtime, and produce an AAR.

## Task Graph

```
swiftResolve
    ↓
buildSwiftAndroid_arm64v8a  ←─┐
buildSwiftAndroid_x86_64   ←──┤── buildSwiftAndroid (lifecycle task)
buildSwiftAndroid_armeabiv7a ←┘
    ↓
copyJniLibs
    ↓
assembleRelease (Android plugin — produces AAR)
```

## Tasks

### SwiftResolveTask

Runs `swift package resolve` to fetch SPM dependencies (Swinject, swift-java).

```kotlin
abstract class SwiftResolveTask : DefaultTask() {
    @get:InputFile
    abstract val packageSwift: RegularFileProperty  // Package.swift

    @get:OutputDirectory
    abstract val buildCheckouts: DirectoryProperty   // .build/checkouts/

    @TaskAction
    fun resolve() {
        execOperations.exec {
            commandLine("swift", "package", "resolve")
            workingDir(packageSwift.get().asFile.parentFile)
        }
    }
}
```

### SwiftBuildAndroidTask

Cross-compiles Swift for a single Android ABI. One instance per ABI.

```kotlin
abstract class SwiftBuildAndroidTask : DefaultTask() {
    @get:Input
    abstract val abi: Property<String>           // e.g. "arm64-v8a"

    @get:Input
    abstract val swiftTriple: Property<String>   // e.g. "aarch64-unknown-linux-android28"

    @get:Input
    abstract val swiftSdk: Property<String>      // e.g. "swift-6.3-RELEASE_android"

    @get:Input
    abstract val disableSandbox: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty    // .build/<triple>/debug/

    @TaskAction
    fun build() {
        // Uses swiftly to invoke the correct Swift version
        execOperations.exec {
            commandLine(swiftlyPath, "run", "swift", "build",
                "+${swiftVersion}",
                "--swift-sdk", swiftTriple.get(),
                "--disable-sandbox"  // required for JExtract callbacks
            )
            workingDir(projectDir)
        }
    }
}
```

### ABI → Triple Mapping

The plugin maintains this mapping internally:

```kotlin
val ABI_TRIPLES = mapOf(
    "arm64-v8a"   to "aarch64-unknown-linux-android",
    "armeabi-v7a" to "armv7-unknown-linux-android",
    "x86_64"      to "x86_64-unknown-linux-android",
)

fun tripleForAbi(abi: String, minSdk: Int): String =
    "${ABI_TRIPLES[abi]}$minSdk"
```

### CopyJniLibsTask

Bundles the compiled `.so` files + Swift runtime libraries into the `jniLibs/` directory structure.

```kotlin
abstract class CopyJniLibsTask : DefaultTask() {
    @get:InputFiles
    abstract val swiftBuildOutputs: ConfigurableFileCollection  // .build/<triple>/debug/*.so

    @get:Input
    abstract val swiftRuntimeLibs: ListProperty<String>

    @get:Input
    abstract val sdkBundlePath: Property<String>  // path to swift-android SDK bundle

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @TaskAction
    fun copy() {
        // For each ABI:
        //   1. Copy compiled .so files from .build/<triple>/debug/
        //   2. Copy libc++_shared.so from NDK
        //   3. Copy Swift runtime .so files (swiftCore, Foundation, Dispatch, etc.)
    }
}
```

### Swift Runtime Libraries to Bundle

```kotlin
val SWIFT_RUNTIME_LIBS = listOf(
    "swiftCore",
    "swift_Concurrency",
    "swift_StringProcessing",
    "swift_RegexParser",
    "swift_Builtin_float",
    "swift_math",
    "swiftAndroid",
    "dispatch",
    "BlocksRuntime",
    "swiftSwiftOnoneSupport",
    "swiftDispatch",
    "Foundation",
    "FoundationEssentials",
    "FoundationInternationalization",
    "_FoundationICU",
    "swiftSynchronization",
)
```

### JExtract Integration

When `jextract(enabled = true)`:
- JExtractSwiftPlugin runs as part of `swift build` (declared in Package.swift)
- Generated Java sources land in `.build/plugins/outputs/.../JExtractSwiftPlugin/src/generated/java/`
- Plugin adds this directory as a Java srcDir in the Android source set

```kotlin
// In plugin apply():
android.sourceSets.getByName("main") {
    java.srcDir(jextractOutputDir)
    jniLibs.srcDir(jniLibsDir)
}
```

### swiftkit-core Bootstrap

The swift-java dependency requires `swiftkit-core` in Maven Local. The plugin handles this:

1. After `swiftResolve`, locate swift-java checkout in `.build/checkouts/swift-java/`
2. Patch `JavaLanguageVersion` in swift-java's build files to match available JDK
3. Run `./gradlew :SwiftKitCore:publishToMavenLocal`
4. Cache result — skip if `~/.m2/repository/org/swift/swiftkit/swiftkit-core/` exists

### Toolchain Discovery

The plugin discovers Swift toolchain paths:

```kotlin
fun findSwiftly(): String {
    val candidates = listOf(
        "${System.getProperty("user.home")}/.swiftly/bin/swiftly",
        "${System.getProperty("user.home")}/.local/share/swiftly/bin/swiftly",
        "/opt/homebrew/bin/swiftly",
        "/usr/local/bin/swiftly",
    )
    return candidates.firstOrNull { File(it).exists() }
        ?: throw GradleException("swiftly not found. Install from https://swift.org/install")
}

fun findSwiftSdkPath(): String {
    val candidates = listOf(
        "${System.getProperty("user.home")}/Library/org.swift.swiftpm/swift-sdks/",
        "${System.getProperty("user.home")}/.config/swiftpm/swift-sdks/",
        "${System.getProperty("user.home")}/.swiftpm/swift-sdks/",
    )
    return candidates.firstOrNull { File(it).exists() }
        ?: throw GradleException("Swift SDK for Android not found")
}
```

## Steps

1. Implement ABI-to-triple mapping utility
2. Implement toolchain discovery (swiftly, Swift SDK path, NDK path)
3. Create SwiftResolveTask
4. Create SwiftBuildAndroidTask (parameterized per ABI)
5. Create CopyJniLibsTask
6. Wire tasks into Android plugin's preBuild
7. Add JExtract source set wiring
8. Handle swiftkit-core bootstrap
9. Test with SwiftAndroidSdk sources

## Verification

```bash
./gradlew buildSwiftAndroid
# Should produce .so files for each ABI

./gradlew assembleRelease
# Should produce AAR with bundled .so + JExtract Java sources
```
