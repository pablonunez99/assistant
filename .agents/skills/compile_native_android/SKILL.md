---
name: compile_native_android
description: Guides compilation of this Flutter project natively in Termux via Gradle instead of the Flutter SDK.
---
# Compiling Natively on Termux via Gradle

Because Flutter SDK is not supported natively in the Termux environment, this project is compiled as a native Android Gradle project using cached Gradle versions and mock Flutter classes to satisfy Kotlin dependencies.

## Pre-requisites
1. Android SDK path must be set in `android/local.properties`:
   ```properties
   sdk.dir=/data/data/com.termux/files/usr
   ```
2. AAPT2 path override must be set in `android/gradle.properties`:
   ```properties
   android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
   ```

## Setup Mock Flutter Classes
Recreate the following mock classes under `android/app/src/main/kotlin/` before compiling:

* **`io/flutter/embedding/android/FlutterActivity.kt`**:
  ```kotlin
  package io.flutter.embedding.android
  import android.app.Activity
  import io.flutter.embedding.engine.FlutterEngine
  open class FlutterActivity : Activity() {
      open fun configureFlutterEngine(flutterEngine: FlutterEngine) {}
  }
  ```
* **`io/flutter/embedding/android/FlutterView.kt`**:
  ```kotlin
  package io.flutter.embedding.android
  import android.content.Context
  import android.util.AttributeSet
  import android.view.View
  import io.flutter.embedding.engine.FlutterEngine
  class FlutterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
      fun attachToFlutterEngine(flutterEngine: FlutterEngine) {}
      fun detachFromFlutterEngine() {}
  }
  ```
* **`io/flutter/embedding/engine/FlutterEngine.kt`**:
  ```kotlin
  package io.flutter.embedding.engine
  import android.content.Context
  import io.flutter.embedding.engine.dart.DartExecutor
  class FlutterEngine(context: Context) {
      val dartExecutor = DartExecutor()
      val navigationChannel = NavigationChannel()
      fun destroy() {}
      class NavigationChannel {
          fun setInitialRoute(route: String) {}
      }
  }
  ```
* **`io/flutter/embedding/engine/dart/DartExecutor.kt`**:
  ```kotlin
  package io.flutter.embedding.engine.dart
  import io.flutter.plugin.common.BinaryMessenger
  class DartExecutor {
      val binaryMessenger = BinaryMessenger()
      fun executeDartEntrypoint(entrypoint: DartEntrypoint) {}
      class DartEntrypoint {
          companion object {
              @JvmStatic
              fun createDefault(): DartEntrypoint = DartEntrypoint()
          }
      }
  }
  ```
* **`io/flutter/plugin/common/BinaryMessenger.kt`**:
  ```kotlin
  package io.flutter.plugin.common
  class BinaryMessenger
  ```
* **`io/flutter/plugin/common/MethodChannel.kt`**:
  ```kotlin
  package io.flutter.plugin.common
  class MethodChannel(messenger: BinaryMessenger, channelName: String) {
      fun setMethodCallHandler(handler: (MethodCall, Result) -> Unit) {}
      interface Result {
          fun success(result: Any?)
          fun error(errorCode: String, errorMessage: String?, errorDetails: Any?)
          fun notImplemented()
      }
  }
  class MethodCall(val method: String, private val arguments: Any?) {
      @Suppress("UNCHECKED_CAST")
      fun <T> argument(key: String): T? {
          return (arguments as? Map<String, Any?>)?.get(key) as? T
      }
  }
  ```
* **`io/flutter/plugin/common/EventChannel.kt`**:
  ```kotlin
  package io.flutter.plugin.common
  class EventChannel(messenger: BinaryMessenger, channelName: String) {
      fun setStreamHandler(handler: StreamHandler) {}
      interface StreamHandler {
          fun onListen(arguments: Any?, events: EventSink?)
          fun onCancel(arguments: Any?)
      }
      interface EventSink {
          fun success(event: Any?)
          fun error(errorCode: String, errorMessage: String?, errorDetails: Any?)
          fun endOfStream()
      }
  }
  ```

## Modify Build Configuration Files
Apply the following native Android configurations:

1. **`android/settings.gradle.kts`**:
   Remove the `flutter.sdk` checks and loader, and specify standard Android/Kotlin plugins:
   ```kotlin
   pluginManagement {
       repositories {
           google()
           mavenCentral()
           gradlePluginPortal()
       }
   }
   plugins {
       id("com.android.application") version "8.4.0" apply false
       id("org.jetbrains.kotlin.android") version "1.9.22" apply false
       id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
   }
   include(":app")
   ```
2. **`android/build.gradle.kts`**:
   Add the Flutter dependencies Maven repository to allprojects:
   ```kotlin
   allprojects {
       repositories {
           google()
           mavenCentral()
           maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
       }
   }
   ```
3. **`android/app/build.gradle.kts`**:
   Remove the `dev.flutter.flutter-gradle-plugin` plugin and compile dynamically using standard SDK properties (version 34/21) rather than the `flutter.*` references:
   ```kotlin
   plugins {
       id("com.android.application")
       id("kotlin-android")
       id("org.jetbrains.kotlin.kapt")
   }
   android {
       namespace = "com.localai.agent.local_personal_ai_agent"
       compileSdk = 34
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_17
           targetCompatibility = JavaVersion.VERSION_17
       }
       kotlinOptions {
           jvmTarget = JavaVersion.VERSION_17.toString()
       }
       defaultConfig {
           applicationId = "com.localai.agent.local_personal_ai_agent"
           minSdk = 21
           targetSdk = 34
           versionCode = 1
           versionName = "1.0.0"
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("debug")
           }
       }
   }
   dependencies {
       val Room_Version = "2.6.1"
       implementation("androidx.room:room-runtime:$Room_Version")
       implementation("androidx.room:room-ktx:$Room_Version")
       kapt("androidx.room:room-compiler:$Room_Version")
       implementation("androidx.work:work-runtime-ktx:2.9.0")
       implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
       implementation("androidx.core:core-ktx:1.12.0")
   }
   ```
4. **`android/app/src/main/AndroidManifest.xml`**:
   Replace the application class placeholder:
   ```xml
   android:name="android.app.Application"
   ```

## Compilation Commands
Clean and compile using the cached Gradle 8.7 binary:
```bash
# 1. Clean build directories
/data/data/com.termux/files/home/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle clean --no-daemon

# 2. Build the debug APK
/data/data/com.termux/files/home/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle assembleDebug --no-daemon
```

## After Successful Build (Important Cleanup)
Once compilation succeeds:
1. Copy the output APK from `/data/data/com.termux/files/home/Assistant/build/app/outputs/apk/debug/app-debug.apk` to `~/storage/downloads/Assistant-debug.apk`.
2. Restore all original configuration files so that git stays clean and the repository compiles as a Flutter project in other environments:
   ```bash
   git checkout android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml android/build.gradle.kts android/gradle.properties android/settings.gradle.kts
   rm -rf android/local.properties android/app/src/main/kotlin/io/flutter
   ```
