package io.github.junaidapk.playupload

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/** The fields of a Google service-account JSON key that the Play API sign-in needs. */
internal class ServiceAccountKey(
  val clientEmail: String,
  val tokenUri: String,
  val privateKey: PrivateKey,
) {

  /** Builds the signed JWT exchanged for an access token (RFC 7523 bearer grant). */
  fun signedJwt(scope: String, now: Instant = Instant.now()): String {
    val header = b64(JsonOutput.toJson(mapOf("alg" to "RS256", "typ" to "JWT")))
    val claims = b64(
      JsonOutput.toJson(
        mapOf(
          "iss" to clientEmail,
          "scope" to scope,
          "aud" to tokenUri,
          "iat" to now.epochSecond,
          "exp" to now.epochSecond + 3600,
        ),
      ),
    )
    val signingInput = "$header.$claims"
    val signature = Signature.getInstance("SHA256withRSA").run {
      initSign(privateKey)
      update(signingInput.toByteArray())
      sign()
    }
    return "$signingInput.${b64(signature)}"
  }

  companion object {
    fun read(file: File): ServiceAccountKey {
      if (!file.isFile) throw GradleException("Play service-account key not found: $file")
      @Suppress("UNCHECKED_CAST")
      val json = JsonSlurper().parse(file) as Map<String, Any?>
      if (json["type"] != "service_account") {
        throw GradleException("$file is not a service-account key (type=${json["type"]})")
      }
      return ServiceAccountKey(
        clientEmail = json["client_email"] as String,
        tokenUri = json["token_uri"] as? String ?: "https://oauth2.googleapis.com/token",
        privateKey = parsePem(json["private_key"] as String),
      )
    }

    fun parsePem(pem: String): PrivateKey {
      val der = pem.lineSequence()
        .filterNot { it.startsWith("-----") }
        .joinToString("")
        .let { Base64.getMimeDecoder().decode(it) }
      return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun b64(text: String) = b64(text.toByteArray())
    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }
}
