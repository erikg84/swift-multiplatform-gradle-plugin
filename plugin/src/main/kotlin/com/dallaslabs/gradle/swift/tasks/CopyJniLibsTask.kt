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
 * Bundles compiled Swift .so files and Swift runtime libraries into the jniLibs directory
 * structure expected by the Android Gradle plugin.
 *
 * Output structure:
 *   jniLibs/arm64-v8a/libSwiftAndroidSDK.so
 *   jniLibs/arm64-v8a/libswiftCore.so
 *   jniLibs/arm64-v8a/libc++_shared.so
 *   ...
 */
abstract class CopyJniLibsTask : DefaultTask() {

    /** Android ABIs that were built. */
    @get:Input
    abstract val abis: ListProperty<String>

    /** Android minSdk (used to compute triple for build output paths). */
    @get:Input
    abstract val minSdk: Property<Int>

    /** Full path to the Swift SDK bundle (e.g. .../swift-6.3-RELEASE_android.artifactbundle). */
    @get:Input
    abstract val sdkBundleName: Property<String>

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    init {
        description = "Bundles Swift .so files and runtime libraries into jniLibs"
        group = "swift"
    }

    @TaskAction
    fun copy() {
        val outDir = jniLibsDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val sdkBasePath = "${SwiftToolchain.findSwiftSdkPath()}/${sdkBundleName.get()}"

        for (abi in abis.get()) {
            val triple = SwiftToolchain.tripleForAbi(abi, minSdk.get())
            val abiDir = File(outDir, abi)
            abiDir.mkdirs()

            // 1. Copy compiled .so files from Swift build output
            val buildOutputDir = File(project.projectDir, ".build/$triple/debug")
            if (buildOutputDir.exists()) {
                buildOutputDir.listFiles()?.filter { it.extension == "so" }?.forEach { so ->
                    so.copyTo(File(abiDir, so.name), overwrite = true)
                }
                logger.lifecycle("  Copied Swift .so files for $abi from $buildOutputDir")
            }

            // 2. Copy libc++_shared.so from NDK sysroot
            val ndkDir = SwiftToolchain.ABI_NDK_DIR[abi]!!
            val libcppSrc = File("$sdkBasePath/swift-android/ndk-sysroot/usr/lib/$ndkDir/libc++_shared.so")
            if (libcppSrc.exists()) {
                libcppSrc.copyTo(File(abiDir, "libc++_shared.so"), overwrite = true)
            }

            // 3. Copy Swift runtime libraries
            val sdkLibDir = SwiftToolchain.ABI_SDK_LIB_DIR[abi]!!
            for (lib in SwiftToolchain.RUNTIME_LIBS) {
                val src = File("$sdkBasePath/swift-android/swift-resources/usr/lib/$sdkLibDir/android/lib$lib.so")
                if (src.exists()) {
                    src.copyTo(File(abiDir, "lib$lib.so"), overwrite = true)
                }
            }

            logger.lifecycle("  Bundled $abi: ${abiDir.listFiles()?.size ?: 0} .so files")
        }
    }
}
