package com.dallaslabs.gradle.swift

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SwiftMultiplatformPluginTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun writeFile(path: String, content: String) {
        val file = File(testProjectDir, path)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun writeSettings() {
        writeFile("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "test-project"
        """.trimIndent())
    }

    private fun writeMinimalBuild(extraConfig: String = "") {
        writeFile("build.gradle.kts", """
            plugins {
                id("com.dallaslabs.swift-multiplatform")
            }
            swiftMultiplatform {
                moduleName = "TestModule"
                sourcesDir = "Sources/TestModule"
                version = "1.0.0"
                android {
                    namespace("com.test.module")
                }
                $extraConfig
            }
        """.trimIndent())
    }

    private fun writeAndroidManifest() {
        writeFile("src/main/AndroidManifest.xml", """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" />
        """.trimIndent())
    }

    @Test
    fun `ABI to triple mapping is correct`() {
        assertEquals("aarch64-unknown-linux-android28", SwiftToolchain.tripleForAbi("arm64-v8a", 28))
        assertEquals("x86_64-unknown-linux-android28", SwiftToolchain.tripleForAbi("x86_64", 28))
        assertEquals("armv7-unknown-linux-android28", SwiftToolchain.tripleForAbi("armeabi-v7a", 28))
    }

    @Test
    fun `ABI mapping throws on unknown ABI`() {
        assertThrows(org.gradle.api.GradleException::class.java) {
            SwiftToolchain.tripleForAbi("mips", 28)
        }
    }

    @Test
    fun `runtime libs list is not empty`() {
        assertTrue(SwiftToolchain.RUNTIME_LIBS.isNotEmpty())
        assertTrue(SwiftToolchain.RUNTIME_LIBS.contains("swiftCore"))
        assertTrue(SwiftToolchain.RUNTIME_LIBS.contains("Foundation"))
    }
}
