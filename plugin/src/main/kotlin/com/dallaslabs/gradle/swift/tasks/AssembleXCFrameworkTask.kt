package com.dallaslabs.gradle.swift.tasks

import com.dallaslabs.gradle.swift.SwiftToolchain
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Assembles an XCFramework from platform-specific xcarchives.
 *
 * Supports two modes:
 * 1. Standard: Uses xcodebuild -create-xcframework with archives from SwiftBuildIosTask
 * 2. Custom script: Delegates to a user-provided build script for complex cases
 *    (e.g. manual .o file reassembly for SwiftPM libraries)
 */
abstract class AssembleXCFrameworkTask : DefaultTask() {

    /** Framework name (e.g. "SwiftAndroidSDK"). */
    @get:Input
    abstract val frameworkName: Property<String>

    /** Paths to xcarchive directories (device + simulator). */
    @get:Input
    abstract val archivePaths: ListProperty<String>

    /** Optional: path to a custom build script. When set, archives are ignored. */
    @get:Input
    @get:org.gradle.api.tasks.Optional
    abstract val buildScript: Property<String>

    @get:OutputDirectory
    abstract val xcframeworkDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Assembles an XCFramework from platform archives"
        group = "swift"
    }

    @TaskAction
    fun assemble() {
        val output = xcframeworkDir.get().asFile
        if (output.exists()) output.deleteRecursively()

        if (buildScript.isPresent && buildScript.get().isNotBlank()) {
            assembleViaScript(output)
        } else {
            assembleViaXcodebuild(output)
        }

        // Verify output
        val infoPlist = File(output, "Info.plist")
        if (!infoPlist.exists()) {
            throw org.gradle.api.GradleException(
                "XCFramework assembly failed: ${output.absolutePath}/Info.plist not found"
            )
        }
        logger.lifecycle("XCFramework assembled: ${output.absolutePath}")
    }

    private fun assembleViaXcodebuild(output: File) {
        val name = frameworkName.get()
        val args = mutableListOf(
            SwiftToolchain.findXcodebuild(), "-create-xcframework"
        )
        for (archive in archivePaths.get()) {
            args += listOf("-archive", archive, "-framework", "$name.framework")
        }
        args += listOf("-output", output.absolutePath)

        execOperations.exec {
            commandLine(args)
        }
    }

    private fun assembleViaScript(output: File) {
        val script = File(project.projectDir, buildScript.get())
        if (!script.exists()) {
            throw org.gradle.api.GradleException("Build script not found: ${script.absolutePath}")
        }

        logger.lifecycle("Using custom build script: ${script.absolutePath}")
        execOperations.exec {
            commandLine("bash", script.absolutePath, output.parentFile.absolutePath)
            workingDir(project.projectDir)
        }
    }
}
