package io.github.junaidapk.playupload

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * ```
 * playUpload {
 *   serviceAccountKey = file("/path/to/key.json") // default: -PplayKey or $PLAY_SERVICE_ACCOUNT_KEY
 *   closedTrack = "alpha"
 *   releaseNotes = "Bug fixes"                    // -PplayNotes overrides
 *   releaseStatus = "completed"                   // -PplayStatus overrides; "draft" for unpublished apps
 * }
 * ```
 */
abstract class PlayUploadExtension {
  /** Google service-account JSON key with release access in Play Console. */
  abstract val serviceAccountKey: RegularFileProperty

  /** Track used by `playClosed<Variant>`. Default `alpha` (Play's built-in closed track). */
  abstract val closedTrack: Property<String>

  /** Release notes for track uploads. */
  abstract val releaseNotes: Property<String>

  /** Language of [releaseNotes]. Default `en-US`. */
  abstract val releaseNotesLanguage: Property<String>

  /** `completed`, `draft`, `inProgress` or `halted`. Default `completed`. */
  abstract val releaseStatus: Property<String>

  /** Rollout fraction when [releaseStatus] is `inProgress`. Default 0.1. */
  abstract val userFraction: Property<Double>
}
