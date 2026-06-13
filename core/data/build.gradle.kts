plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example.core.data"
  compileSdk = 35

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

ksp {
  arg("room.schemaLocation", "${project.rootDir}/app/src/main/assets")
}

dependencies {
  implementation(project(":core:domain"))
  implementation(project(":core:common"))
  
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  "ksp"(libs.androidx.room.compiler)
  
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.security.crypto)
  
  implementation(libs.koin.android)
  implementation(libs.okhttp)
}
