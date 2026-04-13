package com.dallaslabs.gradle.swift

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class SwiftMultiplatformExtension @Inject constructor(
    objects: ObjectFactory
) {
    /** Swift module name (e.g. "SwiftAndroidSDK"). */
    abstract val moduleName: Property<String>

    /** Path to Swift sources relative to project root (e.g. "Sources/SwiftAndroidSDK"). */
    abstract val sourcesDir: Property<String>

    /** SDK version string (e.g. "1.1.7"). */
    abstract val version: Property<String>

    val android: AndroidConfig = objects.newInstance(AndroidConfig::class.java)
    val ios: IosConfig = objects.newInstance(IosConfig::class.java)
    val publishing: PublishingConfig = objects.newInstance(PublishingConfig::class.java)

    fun android(action: Action<AndroidConfig>) = action.execute(android)
    fun ios(action: Action<IosConfig>) = action.execute(ios)
    fun publishing(action: Action<PublishingConfig>) = action.execute(publishing)
}
