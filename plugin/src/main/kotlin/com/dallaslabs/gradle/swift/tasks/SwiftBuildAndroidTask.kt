package com.dallaslabs.gradle.swift.tasks

import com.dallaslabs.gradle.swift.SwiftToolchain
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Cross-compiles Swift for a single Android ABI using the Swift SDK for Android.
 * One task instance is created per ABI (e.g. buildSwiftAndroid_arm64v8a, buildSwiftAndroid_x86_64).
 */
abstract class SwiftBuildAndroidTask : DefaultTask() {

    /** Android ABI (e.g. "arm64-v8a"). */
    @get:Input
    abstract val abi: Property<String>

    /** Full Swift triple (e.g. "aarch64-unknown-linux-android28"). */
    @get:Input
    abstract val swiftTriple: Property<String>

    /** Swift version to use via swiftly (e.g. "6.3"). */
    @get:Input
    abstract val swiftVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Cross-compiles Swift for an Android ABI"
        group = "swift"
    }

    @TaskAction
    fun build() {
        val triple = swiftTriple.get()
        logger.lifecycle("Building Swift for ${abi.get()} ($triple)...")

        val swiftly = SwiftToolchain.findSwiftly()

        execOperations.exec {
            commandLine(
                swiftly, "run", "swift", "build",
                "+${swiftVersion.get()}",
                "--swift-sdk", triple,
                "--disable-sandbox"  // required for JExtract callback generation
            )
            workingDir(project.projectDir)
        }
    }
}
