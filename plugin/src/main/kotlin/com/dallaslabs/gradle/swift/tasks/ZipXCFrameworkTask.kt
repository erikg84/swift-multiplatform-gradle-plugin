package com.dallaslabs.gradle.swift.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Zips an XCFramework directory and computes its SHA-256 checksum.
 * The checksum is needed for:
 * 1. The binaryTarget(checksum:) in the Gitea source archive's Package.swift
 * 2. Verification by SPM when consumers download the framework
 */
abstract class ZipXCFrameworkTask : DefaultTask() {

    /** Name of the framework (e.g. "SwiftAndroidSDK"). */
    @get:Input
    abstract val frameworkName: Property<String>

    /** Version string for the zip filename. */
    @get:Input
    abstract val version: Property<String>

    @get:InputDirectory
    abstract val xcframeworkDir: DirectoryProperty

    @get:OutputFile
    abstract val zipFile: RegularFileProperty

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Zips an XCFramework and computes its SHA-256 checksum"
        group = "swift"
    }

    @TaskAction
    fun zipAndChecksum() {
        val xcfDir = xcframeworkDir.get().asFile
        val zip = zipFile.get().asFile
        val checksum = checksumFile.get().asFile
        val name = frameworkName.get()

        // Zip the XCFramework
        zip.parentFile.mkdirs()
        if (zip.exists()) zip.delete()

        execOperations.exec {
            commandLine("zip", "-qry", zip.absolutePath, "$name.xcframework")
            workingDir(xcfDir.parentFile)
        }

        logger.lifecycle("Zipped: ${zip.absolutePath} (${zip.length() / 1024 / 1024} MB)")

        // Compute checksum using swift package compute-checksum
        val stdout = ByteArrayOutputStream()
        try {
            execOperations.exec {
                commandLine("swift", "package", "compute-checksum", zip.absolutePath)
                standardOutput = stdout
            }
            checksum.writeText(stdout.toString().trim())
        } catch (e: Exception) {
            // Fallback to shasum if swift not available
            logger.warn("swift package compute-checksum failed, falling back to shasum")
            val shaOut = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("shasum", "-a", "256", zip.absolutePath)
                standardOutput = shaOut
            }
            checksum.writeText(shaOut.toString().trim().split(" ").first())
        }

        logger.lifecycle("Checksum: ${checksum.readText()}")
    }
}
