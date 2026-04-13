package com.dallaslabs.gradle.swift.tasks

import com.dallaslabs.gradle.swift.SwiftToolchain
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Runs `swift test` on the host platform (macOS).
 */
abstract class SwiftTestTask : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Runs Swift package tests on the host platform"
        group = "swift"
    }

    @TaskAction
    fun test() {
        logger.lifecycle("Running swift test...")
        execOperations.exec {
            commandLine(SwiftToolchain.findSwift(), "test")
            workingDir(project.projectDir)
        }
    }
}
