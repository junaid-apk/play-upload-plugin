package io.github.junaidapk.playupload

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * Adds a `play` task group to an Android app module. For each release variant (e.g. `release`, `liveRelease`):
 *
 * - `playCheck<Variant>`    verify access and list tracks (no upload)
 * - `playShare<Variant>`    build the bundle, upload to internal app sharing, print the install link
 * - `playInternal<Variant>` build the bundle, release it on the internal testing track
 * - `playClosed<Variant>`   build the bundle, release it on the closed testing track
 * - `playPromote<Variant>`  move the newest release between tracks without rebuilding (`--from`, `--to`)
 */
class PlayUploadPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val providers = project.providers
    val extension = project.extensions.create("playUpload", PlayUploadExtension::class.java).apply {
      serviceAccountKey.convention(
        project.layout.file(
          providers.gradleProperty("playKey")
            .orElse(providers.environmentVariable("PLAY_SERVICE_ACCOUNT_KEY"))
            .map { File(it) },
        ),
      )
      formFactor.convention("")
      closedTrack.convention(formFactor.map { trackName(it, "alpha") })
      internalTrack.convention(formFactor.map { trackName(it, "internal") })
      releaseNotesLanguage.convention("en-US")
      releaseStatus.convention("completed")
      userFraction.convention(0.1)
    }

    project.pluginManager.withPlugin("com.android.application") {
      // AGP types live in AndroidTasks so this class loads even when AGP isn't on the classpath.
      AndroidTasks.register(project, extension)
    }
  }
}

internal object AndroidTasks {

  fun register(project: Project, extension: PlayUploadExtension) {
    val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    components.onVariants(components.selector().withBuildType("release")) { variant ->
      registerTasks(project, extension, variant)
    }
  }

  private fun registerTasks(project: Project, extension: PlayUploadExtension, variant: ApplicationVariant) {
    val providers = project.providers
    val suffix = variant.name.replaceFirstChar { it.uppercase() }
    val bundle = variant.artifacts.get(SingleArtifact.BUNDLE)

    fun PlayTask.common(what: String) {
      description = "$what (${variant.name})"
      packageName.set(variant.applicationId)
      serviceAccountKey.set(extension.serviceAccountKey)
    }

    fun PlayReleaseTask.release() {
      releaseStatus.set(providers.gradleProperty("playStatus").orElse(extension.releaseStatus))
      releaseNotes.set(providers.gradleProperty("playNotes").orElse(extension.releaseNotes))
      releaseNotesLanguage.set(extension.releaseNotesLanguage)
      userFraction.set(extension.userFraction)
    }

    val tasks = project.tasks
    tasks.register("playCheck$suffix", PlayCheckTask::class.java) {
      common("Checks Google Play access and lists tracks")
    }
    tasks.register("playShare$suffix", PlayShareTask::class.java) {
      common("Builds the bundle and uploads it to internal app sharing")
      this.bundle.set(bundle)
    }
    tasks.register("playInternal$suffix", PlayTrackUploadTask::class.java) {
      common("Builds the bundle and releases it on the internal testing track")
      release()
      this.bundle.set(bundle)
      track.set(extension.internalTrack)
    }
    tasks.register("playClosed$suffix", PlayTrackUploadTask::class.java) {
      common("Builds the bundle and releases it on the closed testing track")
      release()
      this.bundle.set(bundle)
      track.set(extension.closedTrack)
    }
    tasks.register("playPromote$suffix", PlayPromoteTask::class.java) {
      common("Moves the newest release between tracks without rebuilding (--from, --to)")
      release()
      fromTrack.convention(extension.internalTrack)
      toTrack.convention(extension.closedTrack)
    }
  }
}

/** Play names form-factor tracks `<formFactor>:<track>`, e.g. `wear:alpha`; phone tracks have no prefix. */
internal fun trackName(formFactor: String, track: String): String =
  if (formFactor.isBlank()) track else "${formFactor.trim()}:$track"
