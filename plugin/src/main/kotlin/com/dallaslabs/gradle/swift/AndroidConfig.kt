package com.dallaslabs.gradle.swift

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class AndroidConfig {

    /** Android ABIs to cross-compile for (e.g. "arm64-v8a", "x86_64"). */
    abstract val abis: ListProperty<String>

    /** Swift SDK bundle name for Android cross-compilation. */
    abstract val swiftSdk: Property<String>

    /** Swift version to invoke via swiftly (e.g. "6.3"). */
    abstract val swiftVersion: Property<String>

    /** Android minSdk level. Also used in the Swift triple suffix. */
    abstract val minSdk: Property<Int>

    /** Android compileSdk level. */
    abstract val compileSdk: Property<Int>

    /** Android library namespace (e.g. "com.dallaslabs.sdk"). */
    abstract val namespace: Property<String>

    /** Whether to enable JExtractSwiftPlugin for Java binding generation. */
    abstract val jextractEnabled: Property<Boolean>

    /** Swift source files to exclude from Android builds (JExtract incompatible). */
    abstract val excludeFromSwift: ListProperty<String>

    /** Whether to auto-bootstrap and re-publish swiftkit-core to the Maven repo. */
    abstract val republishSwiftkitCore: Property<Boolean>

    init {
        abis.convention(listOf("arm64-v8a", "x86_64"))
        swiftSdk.convention("swift-6.3-RELEASE_android.artifactbundle")
        swiftVersion.convention("6.3")
        minSdk.convention(28)
        compileSdk.convention(36)
        jextractEnabled.convention(true)
        republishSwiftkitCore.convention(true)
    }

    fun abis(vararg values: String) = abis.set(values.toList())
    fun jextract(enabled: Boolean) = jextractEnabled.set(enabled)
    fun excludeFromSwift(vararg files: String) = excludeFromSwift.set(files.toList())
}
