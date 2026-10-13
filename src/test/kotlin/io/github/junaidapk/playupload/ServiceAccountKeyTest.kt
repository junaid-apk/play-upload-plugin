package io.github.junaidapk.playupload

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Instant
import java.util.Base64
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ServiceAccountKeyTest {

  private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()

  private fun keyFile(type: String = "service_account"): File {
    val pem = "-----BEGIN PRIVATE KEY-----\n" +
      Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded) +
      "\n-----END PRIVATE KEY-----\n"
    return createTempFile(suffix = ".json").toFile().apply {
      deleteOnExit()
      writeText(
        JsonOutput.toJson(
          mapOf(
            "type" to type,
            "client_email" to "bot@project.iam.gserviceaccount.com",
            "private_key" to pem,
            "token_uri" to "https://oauth2.googleapis.com/token",
          ),
        ),
      )
    }
  }

  @Test
  fun `signed JWT verifies with the public key and carries the claims`() {
    val key = ServiceAccountKey.read(keyFile())
    val now = Instant.ofEpochSecond(1_700_000_000)
    val (header, claims, signature) = key.signedJwt("scope-x", now).split(".")

    val valid = Signature.getInstance("SHA256withRSA").run {
      initVerify(keyPair.public)
      update("$header.$claims".toByteArray())
      verify(Base64.getUrlDecoder().decode(signature))
    }
    assertTrue(valid)

    @Suppress("UNCHECKED_CAST")
    val decoded = JsonSlurper().parseText(String(Base64.getUrlDecoder().decode(claims))) as Map<String, Any?>
    assertEquals("bot@project.iam.gserviceaccount.com", decoded["iss"])
    assertEquals("scope-x", decoded["scope"])
    assertEquals("https://oauth2.googleapis.com/token", decoded["aud"])
    assertEquals(1_700_003_600, (decoded["exp"] as Number).toInt())
  }

  @Test
  fun `rejects files that are not service-account keys`() {
    assertFailsWith<GradleException> { ServiceAccountKey.read(keyFile(type = "authorized_user")) }
    assertFailsWith<GradleException> { ServiceAccountKey.read(File("/nonexistent/key.json")) }
  }
}
