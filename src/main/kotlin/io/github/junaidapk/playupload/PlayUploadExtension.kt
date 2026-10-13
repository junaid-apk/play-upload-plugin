package io.github.junaidapk.playupload

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * ```
 * playUpload {
 *   serviceAccountKey = file("/path/to/key.json") // default: -PplayKey or $PLAY_SERVICE_ACCOUNT_KEY
 *   formFactor = "wear"                           // a Wear OS module: tracks become wear:internal / wear:alpha
 *   closedTrack = "alpha"
 *   releaseNotes = "Bug fixes"                    // -PplayNotes overrides
 *   releaseStatus = "completed"                   // -PplayStatus overrides; "draft" for unpublished apps
 * }
 * ```
 */
abstract class PlayUploadExtension {
  /** Google service-account JSON key with release access in Play Console. */
  abstract val serviceAccountKey: RegularFileProperty

  /**
   * Form factor of this module's app: empty for phones (default), or `wear`, `tv`, `automotive`.
   * Play keeps a separate set of tracks per form factor, named `<formFactor>:<track>`.
   */
  abstract val formFactor: Property<String>

  /** Track used by `playClosed<Variant>`. Default `alpha` (or `<formFactor>:alpha`). */
  abstract val closedTrack: Property<String>

  /** Track used by `playInternal<Variant>`. Default `internal` (or `<formFactor>:internal`). */
  abstract val internalTrack: Property<String>

  /** Release notes for track uploads. */
  abstract val releaseNotes: Property<String>

  /** Language of [releaseNotes]. Default `en-US`. */
  abstract val releaseNotesLanguage: Property<String>

  /** `completed`, `draft`, `inProgress` or `halted`. Default `completed`. */
  abstract val releaseStatus: Property<String>

  /** Rollout fraction when [releaseStatus] is `inProgress`. Default 0.1. */
  abstract val userFraction: Property<Double>
}
