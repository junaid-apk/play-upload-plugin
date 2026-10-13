plugins {
  `kotlin-dsl`
  `maven-publish`
}

// JitPack builds the tag and serves it as com.github.<owner>:play-upload-plugin:<tag>.
group = "com.github.junaid-apk"
version = "1.0.0"

kotlin { jvmToolchain(17) }

dependencies {
  // Compiled against the oldest supported AGP API; the variant API used here is unchanged through AGP 9.
  compileOnly("com.android.tools.build:gradle-api:8.2.0")
  testImplementation(kotlin("test-junit", embeddedKotlinVersion))
}

gradlePlugin {
  plugins {
    register("playUpload") {
      id = "io.github.junaid-apk.play-upload"
      implementationClass = "io.github.junaidapk.playupload.PlayUploadPlugin"
      displayName = "Play Upload"
      description = "Uploads Android App Bundles to Google Play (internal sharing, testing tracks) via the Play Developer API."
    }
  }
}
