# Workplan Overview

## Project: swift-multiplatform-gradle-plugin

A Gradle plugin that unifies iOS XCFramework and Android AAR generation from a single Swift source tree.

## Workplan Files

| File | Phase | Description |
|------|-------|-------------|
| `01-project-setup.md` | Setup | Plugin repo structure, Gradle config, CI skeleton |
| `02-extension-dsl.md` | Phase 1 | The `swiftMultiplatform {}` DSL extension classes |
| `03-android-tasks.md` | Phase 1 | Swift cross-compilation + AAR assembly tasks |
| `04-ios-tasks.md` | Phase 1 | XCFramework build tasks (xcodebuild archive + create-xcframework) |
| `05-publishing-tasks.md` | Phase 2 | Maven, GCS, and Gitea publishing tasks |
| `06-plugin-publishing.md` | Phase 2 | Publishing the plugin itself to GitHub Packages |
| `07-sdk-migration.md` | Phase 3 | Migrating SwiftAndroidSdk to the plugin (alt_build_system branch) |
| `08-testing.md` | Phase 4 | Gradle TestKit tests, integration tests |
| `09-ci-workflow.md` | Phase 4 | GitHub Actions for plugin CI + SDK CI simplification |

## Estimated Effort

| Phase | What | Effort |
|-------|------|--------|
| Setup | Repo scaffolding, Gradle wrapper, composite build | Small |
| Phase 1 | Extension DSL + Android tasks + iOS tasks | Large — core of the plugin |
| Phase 2 | Publishing tasks + plugin publishing | Medium |
| Phase 3 | SDK migration + validation | Medium |
| Phase 4 | Testing + CI + docs | Medium |

## Dependencies

- JDK 17 (Gradle build)
- Swift 6.3 + Swift SDK for Android (runtime)
- Xcode 16+ (xcodebuild for XCFramework)
- Gradle 8.11+ (composite builds, Kotlin DSL)
- GitHub PAT with `write:packages` scope (GitHub Packages publishing)

## Key Decisions

1. **Composite build** over buildSrc — independent testing, no cache invalidation
2. **GitHub Packages** over Gradle Plugin Portal — private plugin, simpler publishing
3. **Lazy task registration** — TaskProvider everywhere, no eager realization
4. **ExecOperations** over Exec tasks — injectable, testable, proper output capture
5. **Property-based credentials** — `providers.gradleProperty()`, never hardcoded
