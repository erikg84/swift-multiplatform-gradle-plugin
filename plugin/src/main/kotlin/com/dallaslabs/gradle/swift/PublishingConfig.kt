package com.dallaslabs.gradle.swift

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class PublishingConfig @Inject constructor(
    objects: ObjectFactory
) {
    val maven: MavenConfig = objects.newInstance(MavenConfig::class.java)
    val gitea: GiteaConfig = objects.newInstance(GiteaConfig::class.java)

    fun maven(action: Action<MavenConfig>) = action.execute(maven)
    fun gitea(action: Action<GiteaConfig>) = action.execute(gitea)
}

abstract class MavenConfig {
    /** Maven group ID (e.g. "com.dallaslabs.sdk"). */
    abstract val groupId: Property<String>

    /** Maven artifact ID (e.g. "swift-android-sdk"). */
    abstract val artifactId: Property<String>

    /** Maven repository URL (e.g. "https://storage.googleapis.com/dallaslabs-sdk-artifacts/maven"). */
    abstract val repository: Property<String>
}

abstract class GiteaConfig {
    /** Gitea registry base URL (e.g. "http://34.60.86.141:3000"). */
    abstract val registryUrl: Property<String>

    /** Gitea API token for authentication. */
    abstract val token: Property<String>

    /** Swift Package Registry scope (e.g. "dallaslabs-sdk"). */
    abstract val scope: Property<String>

    /** Swift package name within the scope (e.g. "swift-android-sdk"). */
    abstract val packageName: Property<String>

    /** Author name for the Swift Package Registry metadata. */
    abstract val authorName: Property<String>

    init {
        authorName.convention("Dallas Labs")
    }
}
