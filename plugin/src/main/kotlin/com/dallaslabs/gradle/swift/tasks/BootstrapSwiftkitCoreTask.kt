package com.dallaslabs.gradle.swift.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Bootstraps swiftkit-core into Maven Local.
 *
 * swift-java declares a dependency on org.swift.swiftkit:swiftkit-core:1.0-SNAPSHOT.
 * This artifact isn't published anywhere — it must be built from the swift-java checkout
 * and installed to ~/.m2/repository. This task handles the bootstrap, including patching
 * swift-java's hardcoded JavaLanguageVersion to match the available JDK.
 *
 * Skips entirely if swiftkit-core is already in Maven Local.
 */
abstract class BootstrapSwiftkitCoreTask : DefaultTask() {

    @get:OutputDirectory
    abstract val mavenLocalMarker: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Bootstraps swiftkit-core into Maven Local (required by swift-java)"
        group = "swift"

        val home = System.getProperty("user.home")
        mavenLocalMarker.convention(
            project.layout.dir(project.provider { File("$home/.m2/repository/org/swift/swiftkit/swiftkit-core") })
        )
    }

    @TaskAction
    fun bootstrap() {
        val marker = mavenLocalMarker.get().asFile
        if (marker.exists() && marker.listFiles()?.isNotEmpty() == true) {
            logger.lifecycle("swiftkit-core already in Maven Local — skipping bootstrap")
            return
        }

        // Find swift-java checkout
        val checkoutsDir = File(project.projectDir, ".build/checkouts")
        val swiftJavaDir = checkoutsDir.listFiles()
            ?.filter { it.isDirectory && it.name == "swift-java" && File(it, "gradlew").exists() }
            ?.firstOrNull()
            ?: throw org.gradle.api.GradleException(
                "swift-java checkout with gradlew not found under $checkoutsDir\n" +
                "Run 'swift package resolve' first."
            )

        logger.lifecycle("Bootstrapping swiftkit-core from $swiftJavaDir")

        // Detect JDK version
        val javaMajor = System.getProperty("java.version").split(".").first().toIntOrNull() ?: 17

        // Patch JavaLanguageVersion in swift-java build files
        val filesToPatch = listOf(
            File(swiftJavaDir, "BuildLogic/src/main/kotlin/build-logic.java-common-conventions.gradle.kts"),
            File(swiftJavaDir, "SwiftKitCore/build.gradle.kts"),
        )
        for (f in filesToPatch) {
            if (f.exists()) {
                val content = f.readText()
                val patched = content.replace(
                    Regex("""JavaLanguageVersion\.of\(\d+\)"""),
                    "JavaLanguageVersion.of($javaMajor)"
                )
                if (patched != content) {
                    f.writeText(patched)
                    logger.lifecycle("  Patched ${f.name}: JavaLanguageVersion → $javaMajor")
                }
            }
        }

        // Clear stale BuildLogic cache
        File(swiftJavaDir, "BuildLogic/build").deleteRecursively()
        File(swiftJavaDir, ".gradle").deleteRecursively()

        // Publish to Maven Local
        execOperations.exec {
            commandLine(
                "${swiftJavaDir.absolutePath}/gradlew",
                ":SwiftKitCore:publishToMavenLocal",
                "-PskipSamples=true",
                "--no-daemon"
            )
            workingDir(swiftJavaDir)
        }

        logger.lifecycle("swiftkit-core bootstrapped to Maven Local")
    }
}
