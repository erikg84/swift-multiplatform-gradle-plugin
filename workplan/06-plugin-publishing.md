# 06 — Plugin Publishing

## Goal
Publish the Gradle plugin itself to GitHub Packages so consumer SDK projects can apply it.

## Plugin Coordinates

```
Group:    com.dallaslabs
Artifact: swift-multiplatform-gradle-plugin
Plugin ID: com.dallaslabs.swift-multiplatform
```

## Publishing Configuration

Already defined in `plugin/build.gradle.kts`:

```kotlin
gradlePlugin {
    plugins {
        create("swiftMultiplatform") {
            id = "com.dallaslabs.swift-multiplatform"
            implementationClass = "com.dallaslabs.gradle.swift.SwiftMultiplatformPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/erikg84/swift-multiplatform-gradle-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

## What Gets Published

The `java-gradle-plugin` + `maven-publish` plugins together produce:

1. **Plugin JAR**: `com.dallaslabs:swift-multiplatform-gradle-plugin:1.0.0`
2. **Plugin marker**: `com.dallaslabs.swift-multiplatform:com.dallaslabs.swift-multiplatform.gradle.plugin:1.0.0`

Both are required. The marker artifact is what allows consumers to resolve the plugin via the `plugins {}` block.

## Local Publishing (Development)

```bash
cd swift-multiplatform-gradle-plugin
./gradlew :plugin:publishToMavenLocal
```

Publishes to `~/.m2/repository/com/dallaslabs/`. Useful during development when iterating on the plugin + SDK together.

## GitHub Packages Publishing

### Manual (local)
```bash
# In ~/.gradle/gradle.properties:
# gpr.user=erikg84
# gpr.key=ghp_xxxxx (PAT with write:packages scope)

./gradlew :plugin:publishAllPublicationsToGitHubPackagesRepository
```

### Automated (GitHub Actions)

```yaml
# .github/workflows/publish.yml
name: Publish Plugin
on:
  push:
    tags: ['v*']

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Publish to GitHub Packages
        run: ./gradlew :plugin:publishAllPublicationsToGitHubPackagesRepository
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Consumer Setup

SDK projects that want to use the plugin add to their `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            name = "DallasLabsPlugins"
            url = uri("https://maven.pkg.github.com/erikg84/swift-multiplatform-gradle-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
```

Then in `build.gradle.kts`:

```kotlin
plugins {
    id("com.dallaslabs.swift-multiplatform") version "1.0.0"
}
```

## Versioning Strategy

- **Development**: `0.1.0-SNAPSHOT` (publishable to GitHub Packages, overwritable)
- **Release candidates**: `1.0.0-rc1`
- **Releases**: `1.0.0` (tag-triggered, immutable)

## GitHub PAT Requirements

| Scope | Who | Why |
|-------|-----|-----|
| `write:packages` | Plugin maintainer (publish) | Upload plugin JARs |
| `read:packages` | SDK consumers (resolve) | Download plugin JARs |

## Steps

1. Verify `gradlePlugin {}` block produces correct marker artifact
2. Test local publishing: `publishToMavenLocal`
3. Test GitHub Packages publishing with PAT
4. Create CI workflow for tag-triggered publishing
5. Verify consumer can resolve plugin from GitHub Packages
6. Document PAT setup for consumers

## Verification

```bash
# Publish locally
./gradlew :plugin:publishToMavenLocal
ls ~/.m2/repository/com/dallaslabs/swift-multiplatform-gradle-plugin/
ls ~/.m2/repository/com/dallaslabs/swift-multiplatform/ # marker

# Consumer can resolve
cd /path/to/SwiftAndroidSdk
./gradlew tasks --all | grep swift
# Should list: swiftResolve, buildSwiftAndroid, assembleXCFramework, publishAll, etc.
```
