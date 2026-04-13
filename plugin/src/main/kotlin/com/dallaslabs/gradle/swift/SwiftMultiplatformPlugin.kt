package com.dallaslabs.gradle.swift

import com.android.build.gradle.LibraryExtension
import com.dallaslabs.gradle.swift.tasks.*
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

class SwiftMultiplatformPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create(
            "swiftMultiplatform",
            SwiftMultiplatformExtension::class.java
        )

        project.plugins.apply("com.android.library")

        // Android config must be set eagerly (before afterEvaluate)
        // because AGP validates compileSdk during configuration phase.
        configureAndroidDefaults(project, ext)

        project.afterEvaluate {
            validate(ext)
            finalizeAndroid(project, ext)
            registerSwiftTasks(project, ext)
            registerIosTasks(project, ext)
            registerPublishingTasks(project, ext)
            registerLifecycleTasks(project, ext)
        }
    }

    private fun validate(ext: SwiftMultiplatformExtension) {
        if (!ext.moduleName.isPresent) throw GradleException("swiftMultiplatform.moduleName must be set")
        if (!ext.sourcesDir.isPresent) throw GradleException("swiftMultiplatform.sourcesDir must be set")
        if (!ext.version.isPresent) throw GradleException("swiftMultiplatform.version must be set")
    }

    /**
     * Sets Android defaults eagerly during configuration phase.
     * AGP requires compileSdk and namespace before afterEvaluate.
     */
    private fun configureAndroidDefaults(project: Project, ext: SwiftMultiplatformExtension) {
        val android = project.extensions.getByType(LibraryExtension::class.java)
        val cfg = ext.android

        // These use convention values set in AndroidConfig.init{}
        android.compileSdk = cfg.compileSdk.get()
        android.namespace = cfg.namespace.getOrElse("com.dallaslabs.sdk")
        android.defaultConfig.minSdk = cfg.minSdk.get()
        android.compileOptions.sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        android.compileOptions.targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }

    /**
     * Wires JExtract and JNI source directories after the build script is evaluated
     * (extension values are now final).
     */
    private fun finalizeAndroid(project: Project, ext: SwiftMultiplatformExtension) {
        val jextractOutputDir = project.file(
            ".build/plugins/outputs/${project.name.lowercase()}/${ext.moduleName.get()}/destination/JExtractSwiftPlugin/src/generated/java"
        )
        val jniLibsDir = project.file("${project.layout.buildDirectory.get().asFile}/generated/jniLibs")

        val mainSourceSet = android.sourceSets.getByName("main")
        mainSourceSet.java.srcDir(jextractOutputDir)
        mainSourceSet.jniLibs.srcDir(jniLibsDir)
    }

    private fun registerSwiftTasks(project: Project, ext: SwiftMultiplatformExtension) {
        val cfg = ext.android

        val swiftResolve = project.tasks.register("swiftResolve", SwiftResolveTask::class.java)

        val bootstrapSwiftkitCore = project.tasks.register(
            "bootstrapSwiftkitCore", BootstrapSwiftkitCoreTask::class.java
        )
        bootstrapSwiftkitCore.configure { dependsOn(swiftResolve) }

        val androidBuildTasks = cfg.abis.get().map { abi ->
            val taskName = "buildSwiftAndroid_${abi.replace("-", "")}"
            val triple = SwiftToolchain.tripleForAbi(abi, cfg.minSdk.get())

            project.tasks.register(taskName, SwiftBuildAndroidTask::class.java).also { tp ->
                tp.configure {
                    dependsOn(swiftResolve, bootstrapSwiftkitCore)
                    this.abi.set(abi)
                    swiftTriple.set(triple)
                    swiftVersion.set(cfg.swiftVersion)
                    outputDir.set(project.layout.projectDirectory.dir(".build/$triple/debug"))
                }
            }
        }

        val buildSwiftAndroid = project.tasks.register("buildSwiftAndroid")
        buildSwiftAndroid.configure {
            description = "Cross-compiles Swift for all Android ABIs"
            group = "swift"
            androidBuildTasks.forEach { dependsOn(it) }
        }

        val copyJniLibs = project.tasks.register("copyJniLibs", CopyJniLibsTask::class.java)
        copyJniLibs.configure {
            dependsOn(buildSwiftAndroid)
            abis.set(cfg.abis)
            minSdk.set(cfg.minSdk)
            sdkBundleName.set(cfg.swiftSdk)
            jniLibsDir.set(project.layout.buildDirectory.dir("generated/jniLibs"))
        }

        project.tasks.named("preBuild").configure { dependsOn(copyJniLibs) }
    }

    private fun registerIosTasks(project: Project, ext: SwiftMultiplatformExtension) {
        val iosConfig = ext.ios
        val frameworkName = iosConfig.frameworkName.getOrElse(ext.moduleName.get())
        val buildDir = project.layout.buildDirectory
        val version = ext.version.get()

        val buildIosDevice = project.tasks.register("buildIosDevice", SwiftBuildIosTask::class.java)
        buildIosDevice.configure {
            scheme.set(ext.moduleName)
            destination.set("generic/platform=iOS")
            minimumDeployment.set(iosConfig.minimumDeployment)
            archivePath.set(buildDir.dir("archives/ios-device.xcarchive"))
        }

        val buildIosSimulator = project.tasks.register("buildIosSimulator", SwiftBuildIosTask::class.java)
        buildIosSimulator.configure {
            scheme.set(ext.moduleName)
            destination.set("generic/platform=iOS Simulator")
            minimumDeployment.set(iosConfig.minimumDeployment)
            archivePath.set(buildDir.dir("archives/ios-simulator.xcarchive"))
        }

        val deviceArchivePath = buildDir.map { it.dir("archives/ios-device.xcarchive").asFile.absolutePath }
        val simArchivePath = buildDir.map { it.dir("archives/ios-simulator.xcarchive").asFile.absolutePath }

        val assembleXCFramework = project.tasks.register("assembleXCFramework", AssembleXCFrameworkTask::class.java)
        assembleXCFramework.configure {
            dependsOn(buildIosDevice, buildIosSimulator)
            this.frameworkName.set(frameworkName)
            archivePaths.set(project.provider {
                listOf(deviceArchivePath.get(), simArchivePath.get())
            })
            if (iosConfig.buildScript.isPresent) {
                buildScript.set(iosConfig.buildScript)
            }
            xcframeworkDir.set(buildDir.dir("xcframeworks/$frameworkName.xcframework"))
        }

        val zipXCFramework = project.tasks.register("zipXCFramework", ZipXCFrameworkTask::class.java)
        zipXCFramework.configure {
            dependsOn(assembleXCFramework)
            this.frameworkName.set(frameworkName)
            this.version.set(ext.version)
            xcframeworkDir.set(buildDir.dir("xcframeworks/$frameworkName.xcframework"))
            zipFile.set(buildDir.file("xcframeworks/$frameworkName-$version.xcframework.zip"))
            checksumFile.set(buildDir.file("xcframeworks/$frameworkName-$version.xcframework.sha256"))
        }
    }

    private fun registerPublishingTasks(project: Project, ext: SwiftMultiplatformExtension) {
        val pubConfig = ext.publishing
        val frameworkName = ext.ios.frameworkName.getOrElse(ext.moduleName.get())
        val version = ext.version.get()

        if (pubConfig.maven.repository.isPresent) {
            project.plugins.apply("maven-publish")
            val publishingExt = project.extensions.getByType(PublishingExtension::class.java)

            val pub = publishingExt.publications.create("release", MavenPublication::class.java)
            pub.from(project.components.findByName("release"))
            pub.groupId = pubConfig.maven.groupId.get()
            pub.artifactId = pubConfig.maven.artifactId.get()
            pub.version = version

            val mavenRepo = publishingExt.repositories.maven { }
            mavenRepo.name = "GCS"
            mavenRepo.url = project.uri(pubConfig.maven.repository.get())
        }

        val publishAndroid = project.tasks.register("publishAndroid")
        publishAndroid.configure {
            description = "Publishes Android AAR to Maven repository"
            group = "swift publishing"
            dependsOn("publishReleasePublicationToGCSRepository")
        }

        val publishIosGcs = project.tasks.register("publishIosGcs", PublishGcsTask::class.java)
        publishIosGcs.configure {
            dependsOn("zipXCFramework")
            zipFile.set(project.layout.buildDirectory.file("xcframeworks/$frameworkName-$version.xcframework.zip"))

            if (pubConfig.maven.repository.isPresent) {
                val repoBase = pubConfig.maven.repository.get().removeSuffix("/")
                val group = pubConfig.maven.groupId.getOrElse("").replace(".", "/")
                val artifact = pubConfig.maven.artifactId.getOrElse(frameworkName.lowercase())
                gcsDestination.set(
                    "${repoBase.replace("https://storage.googleapis.com/", "gs://")}/$group/$artifact-ios/$version/$artifact-ios-$version.zip"
                )
            }
        }

        if (pubConfig.gitea.registryUrl.isPresent) {
            val publishIosGitea = project.tasks.register("publishIosGitea", PublishGiteaTask::class.java)
            publishIosGitea.configure {
                dependsOn("zipXCFramework")
                registryUrl.set(pubConfig.gitea.registryUrl)
                token.set(pubConfig.gitea.token)
                scope.set(pubConfig.gitea.scope)
                packageName.set(pubConfig.gitea.packageName)
                this.version.set(ext.version)
                this.frameworkName.set(frameworkName)
                minimumDeployment.set(ext.ios.minimumDeployment)
                authorName.set(pubConfig.gitea.authorName)

                if (pubConfig.maven.repository.isPresent) {
                    val repoBase = pubConfig.maven.repository.get()
                    val group = pubConfig.maven.groupId.getOrElse("").replace(".", "/")
                    val artifact = pubConfig.maven.artifactId.getOrElse(frameworkName.lowercase())
                    xcframeworkGcsUrl.set(
                        "$repoBase/$group/$artifact-ios/$version/$artifact-ios-$version.zip"
                    )
                }

                xcframeworkChecksum.set(project.provider {
                    val f = project.layout.buildDirectory
                        .file("xcframeworks/$frameworkName-$version.xcframework.sha256")
                        .get().asFile
                    if (f.exists()) f.readText().trim() else ""
                })
            }
        }
    }

    private fun registerLifecycleTasks(project: Project, ext: SwiftMultiplatformExtension) {
        project.tasks.register("buildAll").configure {
            description = "Builds both Android AAR and iOS XCFramework"
            group = "swift"
            dependsOn("assembleRelease", "assembleXCFramework")
        }

        project.tasks.register("swiftTest", SwiftTestTask::class.java)

        project.tasks.register("publishAll").configure {
            description = "Publishes all artifacts to Maven + GCS + Gitea"
            group = "swift publishing"
            dependsOn("publishAndroid", "publishIosGcs")
            if (ext.publishing.gitea.registryUrl.isPresent) {
                dependsOn("publishIosGitea")
            }
        }
    }
}
