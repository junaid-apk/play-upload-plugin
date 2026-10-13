package io.github.junaidapk.playupload

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/** Common inputs: which app and which key. Never up to date — every run talks to Play. */
@DisableCachingByDefault(because = "Talks to Google Play")
abstract class PlayTask : DefaultTask() {
  @get:Input
  abstract val packageName: Property<String>

  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val serviceAccountKey: RegularFileProperty

  init {
    group = "play"
    outputs.upToDateWhen { false }
  }

  internal fun client() = PlayClient(ServiceAccountKey.read(serviceAccountKey.get().asFile))
}

/** Tasks that create a release on a track. */
abstract class PlayReleaseTask : PlayTask() {
  @get:Input
  abstract val releaseStatus: Property<String>

  @get:Input
  @get:Optional
  abstract val releaseNotes: Property<String>

  @get:Input
  abstract val releaseNotesLanguage: Property<String>

  @get:Input
  abstract val userFraction: Property<Double>

  internal fun releaseSpec() = ReleaseSpec(
    status = releaseStatus.get(),
    notes = releaseNotes.orNull?.takeIf { it.isNotBlank() },
    notesLanguage = releaseNotesLanguage.get(),
    userFraction = userFraction.get(),
  )
}

abstract class PlayCheckTask : PlayTask() {
  @TaskAction
  fun check() {
    val pkg = packageName.get()
    val tracks = client().tracks(pkg)
    logger.lifecycle("✓ Play access OK for $pkg")
    tracks.forEach { track ->
      @Suppress("UNCHECKED_CAST")
      val releases = (track["releases"] as? List<Map<String, Any?>>).orEmpty()
        .joinToString(", ") { "${it["status"]} ${it["versionCodes"] ?: "[]"}" }
      logger.lifecycle("  %-12s %s".format(track["track"], releases.ifEmpty { "(empty)" }))
    }
  }
}

abstract class PlayShareTask : PlayTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val bundle: RegularFileProperty

  @TaskAction
  fun share() {
    val file = bundle.get().asFile
    logger.lifecycle("Uploading ${file.name} to internal app sharing…")
    val link = client().uploadToInternalSharing(packageName.get(), file)
    logger.lifecycle("✓ Install link:\n  $link")
  }
}

abstract class PlayTrackUploadTask : PlayReleaseTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val bundle: RegularFileProperty

  @get:Input
  abstract val track: Property<String>

  @TaskAction
  fun upload() {
    val file = bundle.get().asFile
    logger.lifecycle("Uploading ${file.name} to track '${track.get()}'…")
    val spec = releaseSpec()
    val versionCode = client().uploadToTrack(packageName.get(), file, track.get(), spec)
    logger.lifecycle("✓ versionCode $versionCode → '${track.get()}' (${spec.status})")
  }
}

abstract class PlayPromoteTask : PlayReleaseTask() {
  @get:Input
  @get:Option(option = "from", description = "Track to copy the newest release from (default: internal).")
  abstract val fromTrack: Property<String>

  @get:Input
  @get:Option(option = "to", description = "Track to release it on (default: the closed track).")
  abstract val toTrack: Property<String>

  @TaskAction
  fun promote() {
    val codes = client().promote(packageName.get(), fromTrack.get(), toTrack.get(), releaseSpec())
    logger.lifecycle("✓ versionCode ${codes.joinToString()} '${fromTrack.get()}' → '${toTrack.get()}' (${releaseStatus.get()})")
  }
}
