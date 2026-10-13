# Play Upload Gradle plugin

Upload Android App Bundles to Google Play from Gradle or Android Studio: internal app sharing, internal testing, closed testing, and promoting between tracks. It uses the Google Play Developer API with a service-account key and has no other dependencies.

## Tasks

The plugin adds these tasks to the **`play`** group for each release variant, such as `release` or `liveRelease`:

| Task | What it does |
|---|---|
| `playCheck<Variant>` | Checks API access and lists every track. Uploads nothing. |
| `playShare<Variant>` | Builds the signed bundle, uploads it to **internal app sharing** and prints the install link. |
| `playInternal<Variant>` | Builds the bundle and releases it on the **internal testing** track. |
| `playClosed<Variant>` | Builds the bundle and releases it on the **closed testing** track (`alpha` by default). |
| `playPromote<Variant>` | Copies the newest release from one track to another without rebuilding: `--from internal --to alpha`. |

In Android Studio, open the Gradle tool window and go to **app → Tasks → play**, then double-click a task.

```bash
./gradlew playShareRelease
./gradlew playInternalRelease -PplayNotes="Fixes the widget crash"
./gradlew playPromoteRelease --from internal --to alpha
```

## Setup

### 1. Service account (once per Play account)
1. In Google Cloud, create a project, enable the **Google Play Android Developer API**, and create a service account. It needs no Cloud roles.
2. Create a **JSON key** for it. Keep the key outside every repo, for example in `~/.config/play/<account>.json` with `chmod 600`.
3. In **Play Console → Users & permissions**, invite the service account's email and grant **Release to testing tracks**. Add **Release to production** too if you want production uploads.
4. A new app's **first** build has to be uploaded by hand in Play Console. The API can only reach apps that already have a build.

### 2. Point Gradle at the key (once per machine)
Add the key path to `~/.gradle/gradle.properties`:
```properties
playKey=/Users/you/.config/play/<account>.json
```
You can also set `PLAY_SERVICE_ACCOUNT_KEY` in the environment, or set `serviceAccountKey` in the `playUpload { }` block.

### 3. Apply the plugin (per app)
JitPack serves the plugin as a plain module, so map the plugin ID to it in `settings.gradle.kts`:
```kotlin
pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    maven {
      url = uri("https://jitpack.io")
      content { includeGroup("com.github.junaid-apk") }
    }
  }
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "io.github.junaid-apk.play-upload") {
        useModule("com.github.junaid-apk:play-upload-plugin:${requested.version}")
      }
    }
  }
}
```
Then add the plugin in `app/build.gradle.kts`:
```kotlin
plugins {
  id("com.android.application")
  id("io.github.junaid-apk.play-upload") version "1.0.1"
}
```
If you use a version catalog:
```toml
[versions]
playUpload = "1.0.1"
[plugins]
play-upload = { id = "io.github.junaid-apk.play-upload", version.ref = "playUpload" }
```

## Options

```kotlin
playUpload {
  serviceAccountKey = file("/path/to/key.json") // default: -PplayKey / $PLAY_SERVICE_ACCOUNT_KEY
  formFactor = "wear"                           // Wear OS module: uses wear:internal / wear:alpha (default: phone)
  closedTrack = "alpha"                         // or the name of a custom closed track
  internalTrack = "internal"
  releaseNotes = "Bug fixes"
  releaseNotesLanguage = "en-US"
  releaseStatus = "completed"                   // "draft" for apps that were never published
  userFraction = 0.1                            // used with releaseStatus = "inProgress"
}
```
Command-line overrides: `-PplayNotes="..."`, `-PplayStatus=draft`, `-PplayKey=/path.json`.

## Errors

Play API errors are shown in full, with a hint for the common ones:

| Code | Meaning |
|---|---|
| 401 | The key was rejected. Create a new key. |
| 403 | The service account has no permission for this app in Play Console. |
| 404 | The app isn't on this Play account, or it has never had a build. Do the first upload by hand. |

## Compatibility
- Built against AGP 8.2 and tested with AGP 9.1 on Gradle 9.3.
- Needs JDK 17 or newer.
- Supports the configuration cache.

## Development
```bash
./gradlew build publishToMavenLocal   # tests + install as com.github.junaid-apk:play-upload-plugin:<version>
```
Consumers then add `mavenLocal { content { includeGroup("com.github.junaid-apk") } }` to `pluginManagement.repositories`.

To release, bump `version` in `build.gradle.kts`, commit, and push a tag with the same name. JitPack builds the tag.
