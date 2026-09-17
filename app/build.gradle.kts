plugins { id("com.android.application") }
val releaseKeys = providers.environmentVariable("FOLDUO_SIGNING_DIR").orNull
val debugKeyFile = rootProject.file(".local-signing/debug.keystore")
val createDebugKey = tasks.register<Exec>("createLocalDebugKey") {
 outputs.file(debugKeyFile)
 onlyIf { !debugKeyFile.exists() }
 doFirst { debugKeyFile.parentFile.mkdirs() }
 commandLine("${System.getProperty("java.home")}/bin/keytool", "-genkeypair", "-noprompt",
  "-keystore", debugKeyFile.absolutePath, "-storepass", "android", "-keypass", "android",
  "-alias", "androiddebugkey", "-keyalg", "RSA", "-validity", "10000",
  "-dname", "CN=Android Debug,O=Android,C=US")
}
tasks.configureEach { if (name == "validateSigningDebug") dependsOn(createDebugKey) }
android {
 namespace = "jp.bunkaich.sukashimotion"
 compileSdk = 37
 buildToolsVersion = "36.0.0"
 defaultConfig {
  applicationId = "io.github.iam3301.folduo"
  minSdk = 33
  targetSdk = 36
  versionCode = 44
  versionName = "0.2.1-zh-glass"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 buildFeatures { buildConfig = true; aidl = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 signingConfigs {
  getByName("debug") { storeFile = debugKeyFile }
  if (releaseKeys != null) create("chineseRelease") {
   storeFile = file("$releaseKeys/folduo-release.jks")
   storePassword = file("$releaseKeys/password.txt").readText().trim()
   keyAlias = "folduo"
   keyPassword = storePassword
  }
 }
 buildTypes { release {
  isMinifyEnabled = false
  if (releaseKeys != null) signingConfig = signingConfigs.getByName("chineseRelease")
 } }
}
dependencies {
 implementation("dev.rikka.shizuku:api:13.1.5")
 implementation("dev.rikka.shizuku:provider:13.1.5")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation("androidx.test:rules:1.7.0")
}

// Preserve the same notices in both the source distribution and the APK.
val licenseAssets = tasks.register<Sync>("prepareLicenseAssets") {
 from(rootProject.file("LICENSE"))
 from(rootProject.file("THIRD_PARTY_NOTICES.md"))
 from(rootProject.file("licenses"))
 into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
android.sourceSets.getByName("main").assets.directories.add(layout.buildDirectory.dir("generated/licenseAssets").get().asFile.path)
tasks.named("preBuild").configure { dependsOn(licenseAssets) }
