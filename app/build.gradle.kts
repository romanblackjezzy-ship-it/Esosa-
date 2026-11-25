import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.esosa.chat.v3net"
    minSdk = 24
    targetSdk = 36
    val envVersionCode = providers.environmentVariable("ESOSA_VERSION_CODE").orNull?.toIntOrNull() ?: 1
    val envVersionName = providers.environmentVariable("ESOSA_VERSION_NAME").orNull?.takeIf { it.isNotBlank() } ?: "1.0.0"
    val envGithubRepo = providers.environmentVariable("GITHUB_REPOSITORY").orNull?.trim()?.replace("\"", "").orEmpty()
    val envUpdateManifestUrl = providers.environmentVariable("ESOSA_UPDATE_MANIFEST_URL").orNull?.trim()?.replace("\"", "").orEmpty()
    versionCode = envVersionCode
    versionName = envVersionName
    buildConfigField("String", "GITHUB_REPOSITORY", "\"$envGithubRepo\"")
    buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$envUpdateManifestUrl\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val envKeyPath = System.getenv("KEYSTORE_PATH")
      val fallbackPath = if (file("${rootDir}/my-upload-key.jks").exists()) "${rootDir}/my-upload-key.jks" else "${rootDir}/debug.keystore"
      val keystorePath = envKeyPath ?: fallbackPath
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD") ?: "android"
      keyAlias = if (envKeyPath != null || file("${rootDir}/my-upload-key.jks").exists()) "upload" else "androiddebugkey"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all { it.maxHeapSize = "2g" }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

abstract class PrintSigningFingerprintsTask : DefaultTask() {
  @get:Internal abstract val applicationId: Property<String>
  @get:Internal abstract val googleServicesFile: RegularFileProperty

  @get:Internal abstract val debugKeystoreFile: RegularFileProperty
  @get:Internal abstract val debugKeyAlias: Property<String>
  @get:Internal abstract val debugStorePassword: Property<String>

  @get:Internal abstract val releaseKeystoreFile: RegularFileProperty
  @get:Internal abstract val releaseKeyAlias: Property<String>
  @get:Internal abstract val releaseStorePassword: Property<String>

  @TaskAction
  fun printFingerprints() {
    val appId = applicationId.orNull.orEmpty()
    val gsFile = googleServicesFile.orNull?.asFile
    val gsContent = if (gsFile != null && gsFile.isFile) gsFile.readText() else null

    val firebaseProjectId = gsContent?.let {
      Regex("\"project_id\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.getOrNull(1)
    }
    val registeredHashes = gsContent?.let {
      Regex("\"certificate_hash\"\\s*:\\s*\"([^\"]+)\"").findAll(it)
        .map { m -> m.groupValues[1].lowercase() }
        .toSet()
    }.orEmpty()
    val webClientId = gsContent?.let {
      Regex("\"client_id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"client_type\"\\s*:\\s*3")
        .find(it)?.groupValues?.getOrNull(1)
    }

    println("============================================================")
    println("Esosa Signing Certificate Fingerprints")
    println("Package (applicationId): $appId")
    if (firebaseProjectId != null) {
      println("Firebase project_id:     $firebaseProjectId")
    }
    if (webClientId != null) {
      println("Default Web Client ID:   $webClientId")
    }
    if (registeredHashes.isNotEmpty()) {
      println("google-services.json SHA-1 hashes: ${registeredHashes.joinToString(", ")}")
    }
    println("============================================================")

    inspectVariant(
      variantName = "debug (debugConfig)",
      keystoreFile = debugKeystoreFile.orNull?.asFile,
      alias = debugKeyAlias.orNull.orEmpty(),
      storePassword = debugStorePassword.orNull.orEmpty(),
      registeredHashes = registeredHashes
    )

    inspectVariant(
      variantName = "release (release)",
      keystoreFile = releaseKeystoreFile.orNull?.asFile,
      alias = releaseKeyAlias.orNull.orEmpty(),
      storePassword = releaseStorePassword.orNull.orEmpty(),
      registeredHashes = registeredHashes
    )
  }

  private fun inspectVariant(
    variantName: String,
    keystoreFile: File?,
    alias: String,
    storePassword: String,
    registeredHashes: Set<String>
  ) {
    println()
    println("--- Build Configuration: $variantName ---")
    if (keystoreFile == null || !keystoreFile.isFile) {
      println("Keystore: NOT FOUND (${keystoreFile?.path ?: "unset"})")
      return
    }
    println("Keystore: ${keystoreFile.path}")
    println("Alias:    $alias")

    try {
      val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
      keystoreFile.inputStream().use { input ->
        keyStore.load(input, storePassword.toCharArray())
      }
      val resolvedAlias = when {
        alias.isNotBlank() && keyStore.containsAlias(alias) -> alias
        else -> keyStore.aliases().toList().firstOrNull() ?: ""
      }
      val cert = keyStore.getCertificate(resolvedAlias)
      if (cert == null) {
        println("Error: No certificate found for alias '$alias'.")
        return
      }
      val x509 = cert as? X509Certificate
      if (x509 != null) {
        println("Subject:  ${x509.subjectX500Principal.name}")
      }

      val encoded = cert.encoded
      val sha1Colon = digestHex(encoded, "SHA-1", colonDelimited = true)
      val sha1Raw = digestHex(encoded, "SHA-1", colonDelimited = false).lowercase()
      val sha256Colon = digestHex(encoded, "SHA-256", colonDelimited = true)

      println("SHA-1:    $sha1Colon")
      println("SHA-1 (google-services.json): $sha1Raw")
      println("SHA-256:  $sha256Colon")

      if (registeredHashes.isNotEmpty()) {
        val matches = sha1Raw in registeredHashes
        println("Matches app/google-services.json SHA-1: $matches")
      }
    } catch (e: Exception) {
      println("Error reading keystore: ${e.message}")
    }
  }

  private fun digestHex(bytes: ByteArray, algorithm: String, colonDelimited: Boolean): String {
    val digest = MessageDigest.getInstance(algorithm).digest(bytes)
    val separator = if (colonDelimited) ":" else ""
    return digest.joinToString(separator) { "%02X".format(it) }
  }
}

tasks.register<PrintSigningFingerprintsTask>("printSigningFingerprints") {
  group = "verification"
  description = "Prints SHA-1 and SHA-256 signing fingerprints for debug and release build configurations."
  outputs.upToDateWhen { false }

  applicationId.set(android.defaultConfig.applicationId ?: android.namespace.orEmpty())
  googleServicesFile.set(layout.projectDirectory.file("google-services.json"))

  android.signingConfigs.findByName("debugConfig")?.let { cfg ->
    cfg.storeFile?.let { debugKeystoreFile.set(it) }
    debugKeyAlias.set(cfg.keyAlias.orEmpty())
    debugStorePassword.set(cfg.storePassword.orEmpty())
  }
  android.signingConfigs.findByName("release")?.let { cfg ->
    cfg.storeFile?.let { releaseKeystoreFile.set(it) }
    releaseKeyAlias.set(cfg.keyAlias.orEmpty())
    releaseStorePassword.set(cfg.storePassword.orEmpty())
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.storage)
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.play.app.update)
  implementation(libs.play.app.update.ktx)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
