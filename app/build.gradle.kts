import java.io.FileInputStream
import java.util.Properties
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.byokos.pxqwz"
    minSdk = 24
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val properties = Properties()
      val localPropertiesFile = rootProject.file("local.properties")
      if (localPropertiesFile.exists()) {
        val stream = FileInputStream(localPropertiesFile)
        properties.load(stream)
        stream.close()
      }
      val keystorePath = System.getenv("KEYSTORE_PATH")
        ?: properties.getProperty("keystore.path")
        ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
        ?: properties.getProperty("keystore.password")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
        ?: properties.getProperty("key.password")
    }
    create("debugConfig") {
      val base64File = rootProject.file("debug.keystore.base64")
      val keystoreFile = rootProject.file("debug.keystore")
      if (base64File.exists() && !keystoreFile.exists()) {
        try {
          val encoded = base64File.readText().replace("\\s".toRegex(), "")
          val decoded = Base64.getMimeDecoder().decode(encoded)
          keystoreFile.writeBytes(decoded)
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
      storeFile = keystoreFile
      storePassword = System.getenv("DEBUG_STORE_PASSWORD") ?: "android"
      keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "androiddebugkey"
      keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "android"
      isV1SigningEnabled = true
      isV2SigningEnabled = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
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
    }
  }
}

tasks.withType<Test> {
  systemProperty("room.schemaLocation", "${projectDir}/src/main/assets")
}

ksp {
  arg("room.schemaLocation", "${projectDir}/src/main/assets")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.androidx.security.crypto)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  testImplementation(libs.androidx.room.testing)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  
  implementation(libs.koin.android)
  implementation(libs.koin.compose)

  // Modular Clean Architecture subprojects
  implementation(project(":core:domain"))
  implementation(project(":core:common"))
  implementation(project(":core:data"))
  implementation(project(":feature:chat"))
  implementation(project(":feature:settings"))
}

tasks.register("copyApkToRelease") {
  doLast {
    val srcFile = file("build/outputs/apk/debug/app-debug.apk")
    if (srcFile.exists()) {
      // 1. Copy to .build-outputs
      val dest1Dir = rootProject.file(".build-outputs")
      dest1Dir.mkdirs()
      srcFile.copyTo(File(dest1Dir, "app-debug.apk"), overwrite = true)
      
      println("Successfully copied APK to .build-outputs/app-debug.apk!")
    } else {
      println("Source APK file not found at ${srcFile.absolutePath}")
    }
  }
}

afterEvaluate {
  tasks.named("assembleDebug") {
    finalizedBy("copyApkToRelease")
  }
}

