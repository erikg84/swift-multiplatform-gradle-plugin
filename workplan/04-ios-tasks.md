# 04 — iOS Build Tasks

## Goal
Implement Gradle tasks that build Swift for iOS device + simulator, then assemble into an XCFramework.

## Task Graph

```
swiftResolve
    ↓
buildIosDevice      (xcodebuild archive — ios-arm64)
buildIosSimulator   (xcodebuild archive — ios-simulator-arm64 + x86_64)
    ↓
assembleXCFramework (xcodebuild -create-xcframework)
    ↓
zipXCFramework      (zip + compute checksum)
```

## Tasks

### SwiftBuildIosTask

Archives the Swift package for a specific platform using xcodebuild.

```kotlin
abstract class SwiftBuildIosTask : DefaultTask() {
    @get:Input
    abstract val scheme: Property<String>           // "SwiftAndroidSDK"

    @get:Input
    abstract val destination: Property<String>      // "generic/platform=iOS" or "generic/platform=iOS Simulator"

    @get:Input
    abstract val archiveName: Property<String>      // "ios-device" or "ios-simulator"

    @get:OutputDirectory
    abstract val archivePath: DirectoryProperty     // build/archives/ios-device.xcarchive

    @TaskAction
    fun archive() {
        execOperations.exec {
            commandLine(
                "xcodebuild", "archive",
                "-scheme", scheme.get(),
                "-destination", destination.get(),
                "-archivePath", archivePath.get().asFile.absolutePath,
                "-derivedDataPath", "${project.buildDir}/derivedData",
                "SKIP_INSTALL=NO",
                "BUILD_LIBRARY_FOR_DISTRIBUTION=YES",
                "IPHONEOS_DEPLOYMENT_TARGET=${minimumDeployment.get()}"
            )
            workingDir(project.projectDir)
        }
    }
}
```

### AssembleXCFrameworkTask

Combines device + simulator archives into a single XCFramework.

```kotlin
abstract class AssembleXCFrameworkTask : DefaultTask() {
    @get:InputDirectory
    abstract val deviceArchive: DirectoryProperty    // build/archives/ios-device.xcarchive

    @get:InputDirectory
    abstract val simulatorArchive: DirectoryProperty // build/archives/ios-simulator.xcarchive

    @get:Input
    abstract val frameworkName: Property<String>     // "SwiftAndroidSDK"

    @get:OutputDirectory
    abstract val xcframeworkDir: DirectoryProperty   // build/xcframeworks/SwiftAndroidSDK.xcframework

    @TaskAction
    fun assemble() {
        val output = xcframeworkDir.get().asFile

        // Remove existing xcframework if present
        if (output.exists()) output.deleteRecursively()

        execOperations.exec {
            commandLine(
                "xcodebuild", "-create-xcframework",
                "-archive", "${deviceArchive.get().asFile.absolutePath}",
                "-framework", "${frameworkName.get()}.framework",
                "-archive", "${simulatorArchive.get().asFile.absolutePath}",
                "-framework", "${frameworkName.get()}.framework",
                "-output", output.absolutePath
            )
        }
    }
}
```

### ZipXCFrameworkTask

Zips the XCFramework and computes its SHA-256 checksum (needed for GCS upload + Gitea registry).

```kotlin
abstract class ZipXCFrameworkTask : DefaultTask() {
    @get:InputDirectory
    abstract val xcframeworkDir: DirectoryProperty

    @get:Input
    abstract val frameworkName: Property<String>

    @get:Input
    abstract val version: Property<String>

    @get:OutputFile
    abstract val zipFile: RegularFileProperty       // build/SwiftAndroidSDK-1.1.7.xcframework.zip

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty   // build/SwiftAndroidSDK-1.1.7.xcframework.sha256

    @TaskAction
    fun zipAndChecksum() {
        val name = "${frameworkName.get()}-${version.get()}.xcframework.zip"
        val zip = zipFile.get().asFile

        // Zip
        execOperations.exec {
            commandLine("zip", "-qry", zip.absolutePath, "${frameworkName.get()}.xcframework")
            workingDir(xcframeworkDir.get().asFile.parentFile)
        }

        // Compute checksum via swift package compute-checksum
        val result = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("swift", "package", "compute-checksum", zip.absolutePath)
            standardOutput = result
        }
        checksumFile.get().asFile.writeText(result.toString().trim())
    }
}
```

## Alternative: script-based XCFramework build

The current SDK uses `scripts/build-xcframework.sh` which does a more manual process:
1. Archives each platform with `xcodebuild archive`
2. Extracts `.o` files and swiftmodule from DerivedData
3. Reassembles a static framework manually
4. Wraps with `xcodebuild -create-xcframework`

The plugin should support both approaches:
- **Default**: Standard `xcodebuild archive` + `create-xcframework` (simpler)
- **Custom script**: `ios { buildScript = "scripts/build-xcframework.sh" }` (fallback for complex cases)

If the custom script mode is used, the plugin invokes the script and expects the XCFramework at a configured output path.

## xcodebuild Considerations

### Scheme Discovery
The Swift package's scheme name matches the library product name. The plugin can verify:
```bash
xcodebuild -list -json
```

### Deployment Target
Passed via build setting: `IPHONEOS_DEPLOYMENT_TARGET=15.0`

### Build for Distribution
`BUILD_LIBRARY_FOR_DISTRIBUTION=YES` is required to generate a stable .swiftinterface for the XCFramework. Without it, consumers on different Swift versions may get ABI incompatibilities.

### Signing
XCFrameworks for distribution don't need code signing. `CODE_SIGN_IDENTITY=""` and `CODE_SIGNING_REQUIRED=NO` should be set.

## Steps

1. Implement SwiftBuildIosTask (device archive)
2. Implement SwiftBuildIosTask (simulator archive)
3. Implement AssembleXCFrameworkTask
4. Implement ZipXCFrameworkTask with checksum
5. Add custom build script fallback option
6. Wire task dependencies
7. Test with SwiftAndroidSdk sources
8. Verify XCFramework structure (arm64 device + arm64 simulator)

## Verification

```bash
./gradlew assembleXCFramework
# Should produce build/xcframeworks/SwiftAndroidSDK.xcframework/

./gradlew zipXCFramework
# Should produce .zip + .sha256 files

# Verify XCFramework structure
ls build/xcframeworks/SwiftAndroidSDK.xcframework/
# ios-arm64/  ios-arm64_x86_64-simulator/  Info.plist
```
