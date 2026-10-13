package io.github.junaidapk.playupload

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayUploadPluginTest {

  @Test
  fun `applies without the Android plugin and sets defaults`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("io.github.junaid-apk.play-upload")

    val extension = project.extensions.getByType(PlayUploadExtension::class.java)
    assertEquals("alpha", extension.closedTrack.get())
    assertEquals("completed", extension.releaseStatus.get())
    assertEquals("en-US", extension.releaseNotesLanguage.get())
    assertTrue(project.tasks.none { it.group == "play" })
  }
}
