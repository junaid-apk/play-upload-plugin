package io.github.junaidapk.playupload

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/** What goes into a track release. */
internal data class ReleaseSpec(
  val status: String,
  val notes: String?,
  val notesLanguage: String,
  val userFraction: Double,
)

/** Minimal Google Play Developer API v3 client (edits + internal app sharing). */
internal class PlayClient(private val key: ServiceAccountKey) {

  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
  private val token: String by lazy { fetchAccessToken() }

  /** Uploads to internal app sharing and returns the install link. */
  fun uploadToInternalSharing(packageName: String, artifact: File): String {
    val res = call("POST", "$UPLOAD/internalappsharing/$packageName/artifacts/${kind(artifact)}?uploadType=media", fileBody(artifact), OCTET)
    return res["downloadUrl"] as String
  }

  /** Lists every track with its releases, without changing anything. */
  fun tracks(packageName: String): List<Map<String, Any?>> = withEdit(packageName, commit = false) { edit ->
    @Suppress("UNCHECKED_CAST")
    call("GET", "$API/$packageName/edits/$edit/tracks")["tracks"] as? List<Map<String, Any?>> ?: emptyList()
  }

  /** Uploads [artifact] and releases it on [track]. Returns the uploaded version code. */
  fun uploadToTrack(packageName: String, artifact: File, track: String, spec: ReleaseSpec): String =
    withEdit(packageName, commit = true) { edit ->
      val collection = if (kind(artifact) == "bundle") "bundles" else "apks"
      val uploaded = call("POST", "$UPLOAD/$packageName/edits/$edit/$collection?uploadType=media", fileBody(artifact), OCTET)
      val versionCode = (uploaded["versionCode"] as Number).toLong().toString()
      putRelease(packageName, edit, track, listOf(versionCode), spec)
      versionCode
    }

  /**
   * Re-releases the version codes of the newest release on [fromTrack] to [toTrack], no upload needed.
   * Keeps the source release notes unless [spec] has new ones.
   */
  fun promote(packageName: String, fromTrack: String, toTrack: String, spec: ReleaseSpec): List<String> =
    withEdit(packageName, commit = true) { edit ->
      @Suppress("UNCHECKED_CAST")
      val releases = call("GET", "$API/$packageName/edits/$edit/tracks/$fromTrack")["releases"] as? List<Map<String, Any?>>
      val source = releases.orEmpty().firstOrNull { !(it["versionCodes"] as? List<*>).isNullOrEmpty() }
        ?: throw GradleException("Track '$fromTrack' has no release to promote.")
      @Suppress("UNCHECKED_CAST")
      val codes = (source["versionCodes"] as List<Any>).map { it.toString() }
      @Suppress("UNCHECKED_CAST")
      val notes = source["releaseNotes"] as? List<Map<String, Any?>>
      putRelease(packageName, edit, toTrack, codes, spec, inheritedNotes = notes)
      codes
    }

  private fun putRelease(
    packageName: String,
    edit: String,
    track: String,
    versionCodes: List<String>,
    spec: ReleaseSpec,
    inheritedNotes: List<Map<String, Any?>>? = null,
  ) {
    val release = mutableMapOf<String, Any>("versionCodes" to versionCodes, "status" to spec.status)
    when {
      spec.notes != null -> release["releaseNotes"] = listOf(mapOf("language" to spec.notesLanguage, "text" to spec.notes))
      inheritedNotes != null -> release["releaseNotes"] = inheritedNotes
    }
    if (spec.status == "inProgress" || spec.status == "halted") release["userFraction"] = spec.userFraction
    call("PUT", "$API/$packageName/edits/$edit/tracks/$track", json(mapOf("track" to track, "releases" to listOf(release))), JSON)
  }

  private fun <T> withEdit(packageName: String, commit: Boolean, block: (String) -> T): T {
    val edit = call("POST", "$API/$packageName/edits", json(emptyMap<String, Any>()), JSON)["id"] as String
    var done = false
    try {
      val result = block(edit)
      if (commit) call("POST", "$API/$packageName/edits/$edit:commit")
      done = commit
      return result
    } finally {
      // Read-only or failed edits are discarded so they don't linger in Play Console.
      if (!done) runCatching { call("DELETE", "$API/$packageName/edits/$edit") }
    }
  }

  private fun fetchAccessToken(): String {
    val form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer") + "&assertion=" + enc(key.signedJwt(SCOPE))
    val res = call("POST", key.tokenUri, BodyPublishers.ofString(form), "application/x-www-form-urlencoded", authorized = false)
    return res["access_token"] as String
  }

  private fun call(
    method: String,
    url: String,
    body: BodyPublisher? = null,
    contentType: String? = null,
    authorized: Boolean = true,
  ): Map<String, Any?> {
    val request = HttpRequest.newBuilder(URI(url))
      .timeout(Duration.ofMinutes(10))
      .method(method, body ?: BodyPublishers.noBody())
      .apply {
        if (authorized) header("Authorization", "Bearer $token")
        if (contentType != null) header("Content-Type", contentType)
      }
      .build()
    val response = http.send(request, BodyHandlers.ofString())
    val text = response.body().orEmpty()
    if (response.statusCode() >= 400) throw apiError(response.statusCode(), method, url, text)
    @Suppress("UNCHECKED_CAST")
    return if (text.isBlank()) emptyMap() else JsonSlurper().parseText(text) as Map<String, Any?>
  }

  private fun apiError(code: Int, method: String, url: String, body: String): GradleException {
    val message = runCatching {
      @Suppress("UNCHECKED_CAST")
      val error = (JsonSlurper().parseText(body) as Map<String, Any?>)["error"]
      if (error is Map<*, *>) error["message"] as String else error.toString()
    }.getOrDefault(body.take(500))
    val hint = when (code) {
      401 -> "The service-account key was rejected. Create a new JSON key in Google Cloud."
      403 -> "The service account lacks permission. Grant it access in Play Console > Users & permissions."
      404 -> "The app isn't on this Play account, or has never had a build. Do the first upload manually in Play Console."
      else -> null
    }
    return GradleException(
      buildString {
        append("Play API $code on $method ${url.substringBefore('?')}:\n  $message")
        if (hint != null) append("\n  Hint: $hint")
      },
    )
  }

  private fun kind(file: File) = when (file.extension.lowercase()) {
    "aab" -> "bundle"
    "apk" -> "apk"
    else -> throw GradleException("Expected an .aab or .apk, got $file")
  }

  private fun fileBody(file: File) = BodyPublishers.ofFile(file.toPath())
  private fun json(value: Any) = BodyPublishers.ofString(JsonOutput.toJson(value))
  private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

  private companion object {
    const val API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
    const val UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
    const val SCOPE = "https://www.googleapis.com/auth/androidpublisher"
    const val JSON = "application/json"
    const val OCTET = "application/octet-stream"
  }
}
