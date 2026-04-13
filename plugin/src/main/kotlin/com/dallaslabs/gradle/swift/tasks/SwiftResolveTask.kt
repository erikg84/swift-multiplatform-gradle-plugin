package com.dallaslabs.gradle.swift.tasks

import com.dallaslabs.gradle.swift.SwiftToolchain
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Runs `swift package resolve` to fetch SPM dependencies declared in Package.swift.
 */
abstract class SwiftResolveTask : DefaultTask() {

    @get:OutputDirectory
    abstract val checkoutsDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Resolves Swift Package Manager dependencies"
        group = "swift"
        checkoutsDir.convention(project.layout.projectDirectory.dir(".build/checkouts"))
    }

    @TaskAction
    fun resolve() {
        logger.lifecycle("Resolving Swift package dependencies...")
        execOperations.exec {
            commandLine(SwiftToolchain.findSwift(), "package", "resolve")
            workingDir(project.projectDir)
        }
    }
}
