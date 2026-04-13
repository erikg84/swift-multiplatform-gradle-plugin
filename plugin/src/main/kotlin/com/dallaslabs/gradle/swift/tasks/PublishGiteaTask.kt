package com.dallaslabs.gradle.swift.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Creates a source archive with a Package.swift containing a binaryTarget
 * and uploads it to a Gitea Swift Package Registry.
 *
 * The source archive follows Apple's SE-0292 spec:
 * - Single top-level directory containing Package.swift
 * - Metadata with author.name (required by SPM)
 */
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
    @get:Input abstract val authorName: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Publishes source archive to Gitea Swift Package Registry"
        group = "swift publishing"
        authorName.convention("Dallas Labs")
    }

    @TaskAction
    fun publish() {
        val tempDir = temporaryDir
        val ver = version.get()
        val name = packageName.get()
        val framework = frameworkName.get()
        val iosVer = minimumDeployment.get().substringBefore(".")

        // 1. Create source archive with single top-level directory
        val archiveDir = File(tempDir, "$name-$ver")
        archiveDir.mkdirs()

        val packageSwift = """
            |// swift-tools-version: 5.9
            |import PackageDescription
            |let package = Package(
            |    name: "$framework",
            |    platforms: [.iOS(.v$iosVer), .macOS(.v12)],
            |    products: [
            |        .library(name: "$framework", targets: ["$framework"]),
            |    ],
            |    targets: [
            |        .binaryTarget(
            |            name: "$framework",
            |            url: "${xcframeworkGcsUrl.get()}",
            |            checksum: "${xcframeworkChecksum.get()}"
            |        ),
            |    ]
            |)
        """.trimMargin()

        File(archiveDir, "Package.swift").writeText(packageSwift)

        // 2. Zip with single top-level directory
        val zipFile = File(tempDir, "$name-$ver.zip")
        execOperations.exec {
            commandLine("zip", "-qry", zipFile.absolutePath, archiveDir.name)
            workingDir(tempDir)
        }

        // 3. Build metadata JSON
        val author = authorName.get()
        val givenName = author.substringBefore(" ")
        val familyName = author.substringAfter(" ", "")
        val metadata = """{"@context":["http://schema.org/"],"@type":"SoftwareSourceCode","name":"$name","version":"$ver","description":"$framework for iOS","author":{"@type":"Person","givenName":"$givenName","familyName":"$familyName","name":"$author"},"programmingLanguage":{"@type":"ComputerLanguage","name":"Swift","url":"https://swift.org"}}"""

        // 4. Upload to Gitea
        val url = "${registryUrl.get()}/api/packages/${scope.get()}/swift/${scope.get()}/$name/$ver"
        val stdout = ByteArrayOutputStream()

        execOperations.exec {
            commandLine(
                "curl", "-sS",
                "-o", "/dev/null",
                "-w", "%{http_code}",
                "-X", "PUT",
                "-H", "Accept: application/vnd.swift.registry.v1+json",
                "-H", "Authorization: token ${token.get()}",
                "-F", "metadata=$metadata;type=application/json",
                "-F", "source-archive=@${zipFile.absolutePath};type=application/zip",
                url
            )
            standardOutput = stdout
        }

        val httpCode = stdout.toString().trim()
        when (httpCode) {
            "201" -> logger.lifecycle("Published ${scope.get()}.$name v$ver to Gitea Swift Registry")
            "409" -> logger.lifecycle("Version $ver already exists on Gitea — skipping (idempotent)")
            else -> throw GradleException("Gitea upload failed: HTTP $httpCode for PUT $url")
        }
    }
}
