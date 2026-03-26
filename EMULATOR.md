# Android Emulator Setup

## Overview

Guide to setting up an Android emulator for testing Receipt Scanner. The app requires API 33+ and benefits from camera emulation for receipt scanning.

## Prerequisites

- Android SDK installed (via Android Studio or command line)
- Sufficient disk space (~8 GB per AVD)
- Hardware acceleration support (required for acceptable performance)

## Installing the Emulator

### Via Android Studio

1. Open Android Studio → Tools → SDK Manager
2. SDK Tools tab → check "Android Emulator"
3. SDK Platforms tab → check "Android 14 (API 34)" or "Android 13 (API 33)"
4. Click Apply/OK to install

### Via Command Line

```bash
sdkmanager "emulator" "platform-tools"
sdkmanager "platforms;android-34" "system-images;android-34;google_apis;x86_64"
```

## Creating an AVD (Android Virtual Device)

### Via Android Studio

1. Tools → Device Manager → Create Virtual Device
2. Select hardware: **Pixel 6** (recommended) or Pixel 7
3. Select system image: **API 34** (Android 14) with **Google APIs** target
4. Finish — keep default settings

### Via Command Line

```bash
avdmanager create avd \
  --name "Pixel_6_API_34" \
  --package "system-images;android-34;google_apis;x86_64" \
  --device "pixel_6"
```

### Samsung Galaxy S21-like Experience

For testing with a Samsung-like form factor:

```bash
# Create a custom AVD with S21-like specs
avdmanager create avd \
  --name "Galaxy_S21_API_34" \
  --package "system-images;android-34;google_apis;x86_64" \
  --device "pixel_5"
```

Then edit `~/.android/avd/Galaxy_S21_API_34.avd/config.ini`:

```ini
hw.lcd.density=421
hw.lcd.width=1080
hw.lcd.height=2400
skin.name=1080x2400
hw.lcd.screenSize=6.2
```

## Starting the Emulator

### From Android Studio

Click the play ▶ button next to your AVD in Device Manager.

### From Command Line

```bash
# List available AVDs
emulator -list-avds

# Start an AVD
emulator -avd Pixel_6_API_34

# Start with GPU acceleration (recommended)
emulator -avd Pixel_6_API_34 -gpu host
```

## Installing the App

```bash
# Build the debug APK first
./gradlew assembleDebug

# Install on running emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# Or build and install in one step
./gradlew installDebug
```

## Camera Emulation

Receipt Scanner uses the camera for receipt capture. The emulator provides two options:

### Virtual Scene (Default)

The emulator's default camera shows a virtual 3D room. You can:

1. Open the Extended Controls (⋯ button on emulator toolbar)
2. Go to Camera tab
3. Place an image in the virtual scene for OCR testing

### Host Webcam

Use your computer's webcam as the emulator's camera:

1. Open the Extended Controls
2. Go to Camera → Back Camera → Webcam
3. Point your webcam at a receipt for realistic testing

### Image File Workaround

For automated/consistent testing:

1. Use the gallery import feature in the app instead of camera
2. Push a receipt image to the emulator:

```bash
adb push receipt-sample.jpg /sdcard/Pictures/
```

3. Open the app and use the gallery button to select the image

## Hardware Acceleration

Hardware acceleration is **essential** for acceptable emulator performance.

### macOS

- **Hypervisor.framework** (recommended, built-in on modern macOS)
- Verify: `kextstat | grep -i intel` or check System Information → Software → Extensions
- The emulator uses it automatically on Apple Silicon and Intel Macs with Hypervisor.framework

### Linux

- **KVM** (Kernel-based Virtual Machine)

```bash
# Check KVM support
egrep -c '(vmx|svm)' /proc/cpuinfo  # Should return > 0

# Install KVM
sudo apt install qemu-kvm
sudo adduser $USER kvm

# Verify
kvm-ok
```

### Windows

- **HAXM** (Intel) or **Hyper-V / WHPX** (AMD/Intel)

```
# Install HAXM via SDK Manager or:
sdkmanager "extras;intel;Hardware_Accelerated_Execution_Manager"
```

## Performance Tips

1. **Use x86_64 images** — ARM images are significantly slower on x86 hosts
2. **Enable GPU acceleration** — `emulator -avd <name> -gpu host`
3. **Allocate sufficient RAM** — At least 2 GB for the AVD
4. **Use SSD storage** — Emulator images are I/O intensive
5. **Cold boot vs Quick Boot** — Quick Boot (default) resumes from snapshot, much faster
6. **Close other heavy apps** — The emulator is resource-intensive
7. **On Apple Silicon** — Use ARM64 system images for native performance:
   ```bash
   sdkmanager "system-images;android-34;google_apis;arm64-v8a"
   ```

## Troubleshooting

### Emulator Won't Start

- Verify hardware acceleration is enabled
- Check available disk space (need ~8 GB)
- Try cold boot: `emulator -avd <name> -no-snapshot-load`
- Delete and recreate the AVD if corrupt

### "No suitable emulator found" / ADB Issues

```bash
# Restart ADB server
adb kill-server
adb start-server

# Verify emulator is detected
adb devices
```

### Camera Not Working in Emulator

- Check Extended Controls → Camera settings
- Ensure "Back Camera" is set to "VirtualScene" or "Webcam0"
- Restart the emulator if camera settings were changed

### App Crashes on Launch

- Ensure API level is 33+ (minSdk requirement)
- Use a Google APIs image (ML Kit needs Google Play Services)
- Check logcat: `adb logcat -s "ReceiptScanner"`

### Slow Performance

- Enable hardware acceleration (see above)
- Use x86_64 system image (or arm64-v8a on Apple Silicon)
- Increase emulator RAM in AVD settings
- Use `-gpu host` flag
- Disable emulator animations: Settings → Developer Options → Window/Transition/Animator animation scale → Off

### Google Play Services Missing

ML Kit requires Google Play Services. Use system images with "Google APIs" or "Google Play":

```bash
# Google APIs (recommended)
sdkmanager "system-images;android-34;google_apis;x86_64"

# Google Play (includes Play Store)
sdkmanager "system-images;android-34;google_apis_playstore;x86_64"
```
