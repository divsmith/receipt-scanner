# Building Receipt Scanner

This document covers how to build, run, and troubleshoot the Receipt Scanner Android application.

**Tech stack:** Kotlin 2.1.10, AGP 8.8.2, KSP 2.1.10-1.0.31, Hilt 2.54, Room 2.7.1

## Prerequisites

- **JDK 17** (recommend [Eclipse Temurin/Adoptium](https://adoptium.net/)). Verify with:
  ```bash
  java -version
  ```
  You should see output indicating version 17.x.

- **Android SDK** with the following components installed:
  - Build Tools (latest)
  - Android SDK Platform 33, 34, and 35
  - Android SDK Command-line Tools

- **`ANDROID_HOME` environment variable** must be set, or create a `local.properties` file in the project root with:
  ```properties
  sdk.dir=/path/to/your/Android/sdk
  ```

- **Android Studio Ladybug (2024.2+)** — optional but recommended for the best development experience.

## Clone and Build

```bash
git clone <repo-url>
cd receipt-scanner
./gradlew assembleDebug
```

Output APK location:

```
app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** The first build will download Gradle and all dependencies, which may take several minutes. Subsequent builds benefit from Gradle caching and parallel execution (`org.gradle.caching=true`, `org.gradle.parallel=true`).

## Building Release APK

```bash
./gradlew assembleRelease
```

- Release builds use **ProGuard (R8)** for code minification (`minifyEnabled = true`) and resource shrinking (`shrinkResources = true`).
- You need to configure signing in `app/build.gradle.kts` or via a `keystore.properties` file.
- Output APK location:

  ```
  app/build/outputs/apk/release/app-release.apk
  ```

## Setting Up Android Studio

1. Open Android Studio and select **Open** → navigate to the `receipt-scanner` project root directory.
2. Let the Gradle sync complete — this will download all dependencies and configure the project.
3. Open **SDK Manager** (`Settings → Languages & Frameworks → Android SDK`) and ensure SDK Platforms 33, 34, and 35 are installed.
4. Run configurations for the `app` module are already set up — select a device and click **Run**.

## Running on Device

1. On your Android device, enable **Developer Options** and **USB Debugging**.
2. Connect the device via USB and verify the connection:
   ```bash
   adb devices
   ```
3. Build and install directly:
   ```bash
   ./gradlew installDebug
   ```
   Or use the **Run** button in Android Studio.
4. **Grant camera permission** when prompted on first launch — the app requires it for receipt scanning.

## Running on Emulator

For detailed emulator setup and configuration, see [EMULATOR.md](EMULATOR.md).

**Quick start:** Use Android Studio's **Device Manager** (`Tools → Device Manager`) to create and launch an emulator with API level 33 or higher.

## Common Build Issues

| Issue | Solution |
|-------|----------|
| `SDK location not found` | Set the `ANDROID_HOME` environment variable, or create `local.properties` with `sdk.dir=/path/to/sdk` |
| `Could not determine java version` | Ensure JDK 17 is installed and on your `PATH` |
| Gradle daemon out of memory | Increase `-Xmx` in `gradle.properties` (default: `-Xmx2048m -Dfile.encoding=UTF-8`) |
| KSP / Hilt annotation processing errors | Run a clean build: `./gradlew clean assembleDebug` |
| Room schema export errors | Ensure the `app/schemas` directory exists; Room exports schemas to `$projectDir/schemas` |
| Configuration cache issues | Bypass with: `./gradlew --no-configuration-cache assembleDebug` |

## Gradle Tasks Reference

| Task | Description |
|------|-------------|
| `./gradlew assembleDebug` | Build debug APK |
| `./gradlew assembleRelease` | Build release APK |
| `./gradlew installDebug` | Build and install debug APK on connected device |
| `./gradlew lint` | Run Android lint checks |
| `./gradlew testDebugUnitTest` | Run unit tests (JUnit 5) |
| `./gradlew connectedDebugAndroidTest` | Run instrumented tests on connected device/emulator |
| `./gradlew clean` | Clean all build outputs |
| `./gradlew dependencies` | Show the full dependency tree |
