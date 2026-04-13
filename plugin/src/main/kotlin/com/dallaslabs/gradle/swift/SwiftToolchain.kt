package com.dallaslabs.gradle.swift

import org.gradle.api.GradleException
import java.io.File

/**
 * Utility functions for discovering Swift toolchain paths and mapping ABIs to triples.
 */
object SwiftToolchain {

    /** Maps Android ABI names to Swift target triple prefixes. */
    private val ABI_TRIPLE_PREFIX = mapOf(
        "arm64-v8a" to "aarch64-unknown-linux-android",
        "armeabi-v7a" to "armv7-unknown-linux-android",
        "x86_64" to "x86_64-unknown-linux-android",
    )

    /** Maps Android ABI names to the Swift SDK lib subdirectory name. */
    val ABI_SDK_LIB_DIR = mapOf(
        "arm64-v8a" to "swift-aarch64",
        "armeabi-v7a" to "swift-armv7",
        "x86_64" to "swift-x86_64",
    )

    /** Maps Android ABI names to the NDK lib subdirectory name. */
    val ABI_NDK_DIR = mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "armeabi-v7a" to "arm-linux-android",
        "x86_64" to "x86_64-linux-android",
    )

    /** Swift runtime libraries that must be bundled into the APK/AAR. */
    val RUNTIME_LIBS = listOf(
        "swiftCore",
        "swift_Concurrency",
        "swift_StringProcessing",
        "swift_RegexParser",
        "swift_Builtin_float",
        "swift_math",
        "swiftAndroid",
        "dispatch",
        "BlocksRuntime",
        "swiftSwiftOnoneSupport",
        "swiftDispatch",
        "Foundation",
        "FoundationEssentials",
        "FoundationInternationalization",
        "_FoundationICU",
        "swiftSynchronization",
    )

    /** Returns the full Swift triple for a given ABI and minSdk level. */
    fun tripleForAbi(abi: String, minSdk: Int): String {
        val prefix = ABI_TRIPLE_PREFIX[abi]
            ?: throw GradleException("Unknown ABI: $abi. Supported: ${ABI_TRIPLE_PREFIX.keys}")
        return "$prefix$minSdk"
    }

    /** Finds the swiftly binary on the host machine. */
    fun findSwiftly(): String {
        val home = System.getProperty("user.home")
        val candidates = listOf(
            "$home/.swiftly/bin/swiftly",
            "$home/.local/share/swiftly/bin/swiftly",
            "$home/.local/bin/swiftly",
            "/opt/homebrew/bin/swiftly",
            "/usr/local/bin/swiftly",
        )
        return candidates.firstOrNull { File(it).exists() }
            ?: throw GradleException(
                "swiftly not found. Install from https://swift.org/install\n" +
                "Searched: ${candidates.joinToString(", ")}"
            )
    }

    /** Finds the directory containing installed Swift SDK bundles. */
    fun findSwiftSdkPath(): String {
        val home = System.getProperty("user.home")
        val candidates = listOf(
            "$home/Library/org.swift.swiftpm/swift-sdks/",
            "$home/.config/swiftpm/swift-sdks/",
            "$home/.swiftpm/swift-sdks/",
        )
        return candidates.firstOrNull { File(it).exists() }
            ?: throw GradleException(
                "Swift SDK for Android not found.\n" +
                "Install with: swift sdk install <url>\n" +
                "Searched: ${candidates.joinToString(", ")}"
            )
    }

    /** Finds the swift binary. Returns "swift" if on PATH, or full path. */
    fun findSwift(): String {
        val candidates = listOf(
            "/opt/homebrew/bin/swift",
            "/usr/bin/swift",
        )
        return candidates.firstOrNull { File(it).exists() } ?: "swift"
    }

    /** Finds the xcodebuild binary. */
    fun findXcodebuild(): String {
        val path = "/usr/bin/xcodebuild"
        if (!File(path).exists()) {
            throw GradleException("xcodebuild not found at $path. Is Xcode installed?")
        }
        return path
    }
}
