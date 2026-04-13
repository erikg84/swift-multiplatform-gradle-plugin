# 05 — Publishing Tasks

## Goal
Implement tasks that publish Android AAR to GCS Maven, iOS XCFramework to GCS, and source archive to Gitea Swift Package Registry.

## Task Graph

```
assembleRelease ──→ publishAndroid (AAR → GCS Maven)

zipXCFramework ──→ publishIosGcs (zip → GCS bucket)
                └→ publishIosGitea (source archive → Gitea registry)

publishAll ──→ publishAndroid + publishIosGcs + publishIosGitea
```

## Tasks

### PublishMavenTask (Android AAR → GCS Maven)

Uses Gradle's built-in Maven Publish plugin. The swift-multiplatform plugin configures it automatically.

```kotlin
// In SwiftMultiplatformPlugin.apply():
project.plugins.apply("maven-publish")

project.afterEvaluate {
    project.extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(project.components.getByName("release"))
                groupId = ext.publishing.maven.groupId.get()
                artifactId = ext.publishing.maven.artifactId.get()
                version = ext.version.get()
            }
        }
        repositories {
            maven {
                name = "GCS"
                url = project.uri(ext.publishing.maven.repository.get())
            }
        }
    }
}
```

The consumer runs: `./gradlew publishReleasePublicationToGCSRepository`

Or the plugin registers a convenience task:
```kotlin
val publishAndroid = tasks.register("publishAndroid") {
    dependsOn("publishReleasePublicationToGCSRepository")
}
```

### PublishGcsTask (XCFramework zip → GCS)

Uploads the zipped XCFramework to a GCS bucket using `gcloud storage cp`.

```kotlin
abstract class PublishGcsTask : DefaultTask() {
    @get:InputFile
    abstract val zipFile: RegularFileProperty

    @get:Input
    abstract val gcsBucket: Property<String>    // "gs://dallaslabs-sdk-artifacts"

    @get:Input
    abstract val gcsPath: Property<String>      // "xcframework/SwiftAndroidSDK-1.1.7.xcframework.zip"

    @TaskAction
    fun upload() {
        val destination = "${gcsBucket.get()}/${gcsPath.get()}"
        execOperations.exec {
            commandLine("gcloud", "storage", "cp", zipFile.get().asFile.absolutePath, destination)
        }
        logger.lifecycle("Uploaded XCFramework to $destination")
    }
}
```

### PublishGiteaTask (Source archive → Gitea Swift Registry)

Creates a source archive containing a `Package.swift` with `binaryTarget` pointing at the GCS URL, then uploads to Gitea.

```kotlin
abstract class PublishGiteaTask : DefaultTask() {
    @get:Input abstract val registryUrl: Property<String>
    @get:Input abstract val token: Property<String>
    @get:Input abstract val scope: Property<String>
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val version: Property<String>
    @get:Input abstract val frameworkName: Property<String>
    @get:Input abstract val xcframeworkGcsUrl: Property<String>
    @get:Input abstract val xcframeworkChecksum: Property<String>
    @get:Input abstract val minimumDeployment: Property<String>

    @TaskAction
    fun publish() {
        val tempDir = temporaryDir

        // 1. Create source archive with single top-level directory
        val archiveDir = File(tempDir, "${packageName.get()}-${version.get()}")
        archiveDir.mkdirs()

        // 2. Generate Package.swift with binaryTarget
        File(archiveDir, "Package.swift").writeText("""
            |// swift-tools-version: 5.9
            |import PackageDescription
            |let package = Package(
            |    name: "${frameworkName.get()}",
            |    platforms: [.iOS(.v${minimumDeployment.get().substringBefore(".")}), .macOS(.v12)],
            |    products: [
            |        .library(name: "${frameworkName.get()}", targets: ["${frameworkName.get()}"]),
            |    ],
            |    targets: [
            |        .binaryTarget(
            |            name: "${frameworkName.get()}",
            |            url: "${xcframeworkGcsUrl.get()}",
            |            checksum: "${xcframeworkChecksum.get()}"
            |        ),
            |    ]
            |)
        """.trimMargin())

        // 3. Zip with single top-level directory
        val zipFile = File(tempDir, "${packageName.get()}-${version.get()}.zip")
        execOperations.exec {
            commandLine("zip", "-qry", zipFile.absolutePath, archiveDir.name)
            workingDir(tempDir)
        }

        // 4. Upload to Gitea with metadata
        val url = "${registryUrl.get()}/api/packages/${scope.get()}/swift/${scope.get()}/${packageName.get()}/${version.get()}"
        val metadata = """{"@context":["http://schema.org/"],"@type":"SoftwareSourceCode","name":"${packageName.get()}","version":"${version.get()}","description":"${frameworkName.get()} for iOS","author":{"@type":"Person","givenName":"Dallas","familyName":"Labs","name":"Dallas Labs"},"programmingLanguage":{"@type":"ComputerLanguage","name":"Swift","url":"https://swift.org"}}"""

        execOperations.exec {
            commandLine(
                "curl", "-sS", "-X", "PUT",
                "-H", "Accept: application/vnd.swift.registry.v1+json",
                "-H", "Authorization: token ${token.get()}",
                "-F", "metadata=$metadata;type=application/json",
                "-F", "source-archive=@${zipFile.absolutePath};type=application/zip",
                "-o", "/dev/null", "-w", "%{http_code}",
                url
            )
        }

        logger.lifecycle("Published ${scope.get()}.${packageName.get()} v${version.get()} to Gitea")
    }
}
```

### publishAll Lifecycle Task

```kotlin
val publishAll = tasks.register("publishAll") {
    group = "publishing"
    description = "Publishes Android AAR to GCS Maven and iOS XCFramework to GCS + Gitea"
    dependsOn(publishAndroid, publishIosGcs, publishIosGitea)
}
```

## swiftkit-core Re-Publishing

The Swift SDK's POM declares `org.swift.swiftkit:swiftkit-core:1.0-SNAPSHOT` as a transitive dependency. Since it only exists in the build machine's Maven Local, the plugin optionally re-publishes it to GCS Maven:

```kotlin
swiftMultiplatform {
    android {
        republishSwiftkitCore(true)  // default: true if jextract enabled
    }
}
```

The task finds the JAR in `~/.m2/repository/org/swift/swiftkit/swiftkit-core/1.0-SNAPSHOT/`, stages a mini Gradle project, and publishes it to GCS.

## Credential Handling

All credentials come from Gradle properties or environment variables — never hardcoded:

```kotlin
// In gradle.properties (local, gitignored) or CI env vars
GITEA_URL=http://34.60.86.141:3000
GITEA_TOKEN=abc123
GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json
```

The plugin reads them via `providers.gradleProperty()` or `providers.environmentVariable()`.

## Steps

1. Configure Maven Publish plugin for Android AAR → GCS
2. Implement PublishGcsTask for XCFramework upload
3. Implement PublishGiteaTask for Swift Registry
4. Implement swiftkit-core re-publishing
5. Register `publishAndroid`, `publishIosGcs`, `publishIosGitea`, `publishAll`
6. Wire credential handling
7. Test full publish flow against real GCS + Gitea

## Verification

```bash
./gradlew publishAll
# Should:
# 1. Publish AAR to GCS Maven
# 2. Upload XCFramework zip to GCS
# 3. Upload source archive to Gitea
# All from one command
```
