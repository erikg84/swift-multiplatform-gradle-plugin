package com.dallaslabs.gradle.swift

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class IosConfig {

    /** iOS targets to build (e.g. "ios-arm64", "ios-simulator-arm64"). */
    abstract val targets: ListProperty<String>

    /** Minimum iOS deployment target (e.g. "15.0"). */
    abstract val minimumDeployment: Property<String>

    /** XCFramework name (defaults to moduleName if not set). */
    abstract val frameworkName: Property<String>

    /**
     * Optional path to a custom build script for XCFramework generation.
     * When set, the plugin delegates to this script instead of using xcodebuild directly.
     * The script receives the output directory as its first argument and must produce
     * a valid XCFramework at <outputDir>/<frameworkName>.xcframework.
     */
    abstract val buildScript: Property<String>

    init {
        targets.convention(listOf("ios-arm64", "ios-simulator-arm64"))
        minimumDeployment.convention("15.0")
    }

    fun targets(vararg values: String) = targets.set(values.toList())
}
