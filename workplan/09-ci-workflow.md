# 09 — CI Workflows

## Goal
GitHub Actions workflows for the plugin repo (build/test/publish) and the simplified SDK workflow after migration.

## Plugin Repo Workflows

### ci.yml — Build + Test on PR

```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :plugin:test --no-daemon
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: plugin/build/reports/tests/

  integration-tests:
    needs: unit-tests
    runs-on: [self-hosted, mac-studio]
    steps:
      - uses: actions/checkout@v4
      - run: |
          echo "JAVA_HOME=$(/usr/libexec/java_home -v 17)" >> "$GITHUB_ENV"
      - run: ./gradlew :plugin:test -Pinclude.integration=true --no-daemon
```

### publish.yml — Publish Plugin on Tag

```yaml
name: Publish Plugin
on:
  push:
    tags: ['v*']

permissions:
  packages: write

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4

      - name: Extract version from tag
        id: version
        run: echo "VERSION=${GITHUB_REF#refs/tags/v}" >> "$GITHUB_OUTPUT"

      - name: Publish to GitHub Packages
        run: ./gradlew :plugin:publishAllPublicationsToGitHubPackagesRepository -PVERSION=${{ steps.version.outputs.VERSION }} --no-daemon
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Verify publication
        run: |
          echo "Published com.dallaslabs:swift-multiplatform-gradle-plugin:${{ steps.version.outputs.VERSION }}"
          echo "Consumer setup:"
          echo '  id("com.dallaslabs.swift-multiplatform") version "${{ steps.version.outputs.VERSION }}"'
```

## SDK Repo Workflow (after migration)

### release.yml — Single Job

Before: 2 jobs, ~180 lines, separate Android AAR + iOS XCFramework builds.

After:

```yaml
name: Release
on:
  push:
    tags: ['v*']
  workflow_dispatch:
    inputs:
      version:
        description: 'Release version (e.g. 1.2.0)'
        required: false

permissions:
  contents: read
  packages: read

jobs:
  publish:
    runs-on: [self-hosted, mac-studio]
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Configure environment
        run: |
          JDK17="$(/usr/libexec/java_home -v 17)"
          echo "JAVA_HOME=$JDK17" >> "$GITHUB_ENV"
          echo "$JDK17/bin" >> "$GITHUB_PATH"
          echo "/opt/homebrew/bin" >> "$GITHUB_PATH"

      - name: Override version (workflow_dispatch)
        if: github.event_name == 'workflow_dispatch' && inputs.version != ''
        run: sed -i.bak "s/^VERSION_NAME=.*/VERSION_NAME=${{ inputs.version }}/" gradle.properties

      - name: Authenticate to GCP
        env:
          GCS_SA_KEY_JSON: ${{ secrets.GCS_SA_KEY_JSON }}
        run: |
          echo "$GCS_SA_KEY_JSON" > "$RUNNER_TEMP/gcs-key.json"
          echo "GOOGLE_APPLICATION_CREDENTIALS=$RUNNER_TEMP/gcs-key.json" >> "$GITHUB_ENV"

      - name: Build and publish all artifacts
        run: ./gradlew publishAll --no-daemon
        env:
          GITEA_URL: ${{ secrets.GITEA_URL }}
          GITEA_TOKEN: ${{ secrets.GITEA_TOKEN }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

That's it. One step: `./gradlew publishAll`. The plugin handles:
1. `swift package resolve`
2. Cross-compile for each Android ABI
3. Bundle .so + runtime into AAR
4. JExtract Java source generation
5. Publish AAR to GCS Maven
6. Build iOS device + simulator archives
7. Create XCFramework
8. Zip + checksum
9. Upload XCFramework to GCS
10. Create + upload source archive to Gitea registry

### ci.yml — SDK Tests

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    runs-on: [self-hosted, mac-studio]
    steps:
      - uses: actions/checkout@v4
      - name: Configure JDK 17
        run: echo "JAVA_HOME=$(/usr/libexec/java_home -v 17)" >> "$GITHUB_ENV"
      - name: Swift tests
        run: swift test
        env:
          TMDB_READ_TOKEN: ${{ secrets.TMDB_READ_TOKEN }}
      - name: Build all (no publish)
        run: ./gradlew buildAll --no-daemon
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Comparison

| Metric | Before | After |
|--------|--------|-------|
| release.yml jobs | 2 | 1 |
| release.yml lines | ~180 | ~30 |
| CI steps with shell scripts | 8+ | 1 (`./gradlew publishAll`) |
| Manual xcodebuild invocations | 3 | 0 (plugin handles it) |
| Manual curl to Gitea | 1 | 0 (plugin handles it) |
| Manual gcloud commands | 1 | 0 (plugin handles it) |

## Steps

1. Create `.github/workflows/ci.yml` in plugin repo
2. Create `.github/workflows/publish.yml` in plugin repo
3. Test CI runs on PR
4. Test publish on tag push
5. After SDK migration: replace SDK's release.yml with simplified version
6. After SDK migration: replace SDK's ci.yml with simplified version
7. Verify full end-to-end: tag SDK → CI → publishAll → artifacts on GCS + Gitea
