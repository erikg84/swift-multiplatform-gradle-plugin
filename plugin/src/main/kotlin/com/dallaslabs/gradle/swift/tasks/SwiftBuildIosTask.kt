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
 * Archives a Swift package for an iOS platform using xcodebuild.
 * Creates an .xcarchive containing the compiled framework.
 *
 * Two instances are created: one for device (ios-arm64) and one for simulator.
 */
abstract class SwiftBuildIosTask : DefaultTask() {

    /** Xcode scheme name (matches the SPM library product). */
    @get:Input
    abstract val scheme: Property<String>

    /** Xcodebuild destination (e.g. "generic/platform=iOS" or "generic/platform=iOS Simulator"). */
    @get:Input
    abstract val destination: Property<String>

    /** Minimum iOS deployment target (e.g. "15.0"). */
    @get:Input
    abstract val minimumDeployment: Property<String>

    @get:OutputDirectory
    abstract val archivePath: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Archives Swift framework for an iOS platform"
        group = "swift"
    }

    @TaskAction
    fun archive() {
        val archiveDir = archivePath.get().asFile
        // xcodebuild appends .xcarchive
        val archiveArg = archiveDir.absolutePath.removeSuffix(".xcarchive")

        logger.lifecycle("Building ${scheme.get()} for ${destination.get()}...")

        execOperations.exec {
            commandLine(
                SwiftToolchain.findXcodebuild(),
                "archive",
                "-scheme", scheme.get(),
                "-destination", destination.get(),
                "-archivePath", archiveArg,
                "-derivedDataPath", "${project.layout.buildDirectory.get().asFile}/derivedData",
                "SKIP_INSTALL=NO",
                "BUILD_LIBRARY_FOR_DISTRIBUTION=YES",
                "IPHONEOS_DEPLOYMENT_TARGET=${minimumDeployment.get()}",
                "CODE_SIGN_IDENTITY=",
                "CODE_SIGNING_REQUIRED=NO",
            )
            workingDir(project.projectDir)
        }
    }
}
