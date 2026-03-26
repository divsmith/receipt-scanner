# 🧾 ReceiptScanner

[![CI](https://github.com/<owner>/receipt-scanner/actions/workflows/ci.yml/badge.svg)](https://github.com/<owner>/receipt-scanner/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Receipt Scanner for YNAB** is an Android app that lets you snap a receipt photo, automatically extract merchant name, amount, and date via on-device OCR, fuzzy-match extracted text to your YNAB payees, suggest transaction categories, detect the payment account by card last-4 digits, and submit transactions directly to YNAB. The entire OCR pipeline runs on-device with no cloud dependency, and an offline queue with automatic retry ensures transactions are never lost.

## Features

- 📷 **Camera capture & gallery import** — Take a photo or pick from gallery
- 🔍 **On-device OCR via Google ML Kit** — No cloud dependency; all text recognition happens locally
- 🏪 **Fuzzy payee matching** — Uses Levenshtein distance to match extracted merchant names to YNAB payees
- 📂 **Smart category suggestions** — Suggests categories based on payee transaction history
- 💳 **Automatic account detection** — Identifies the payment account via card last-4 digits on the receipt
- 📡 **Offline transaction queue** — WorkManager-backed queue with automatic retry when connectivity returns
- 🔄 **Delta sync for YNAB data caching** — Efficiently syncs payees, categories, and accounts using server knowledge
- 🎨 **Material 3 UI with Jetpack Compose** — Modern, responsive interface following Material Design 3

## Screenshots

<!-- Add screenshots here -->

## Quick Start

### Prerequisites

- **JDK 17** or newer
- **Android SDK** with API level 35 installed
- An Android device or emulator running **Android 13+** (API 33)

### Build & Install

```bash
# Clone the repository
git clone https://github.com/<owner>/receipt-scanner.git
cd receipt-scanner

# Build the debug APK
./gradlew assembleDebug

# Install on a connected device or emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Configure YNAB

1. Launch the app and navigate to **Settings**
2. Enter your [YNAB Personal Access Token](https://app.ynab.com/settings/developer)
3. Select your budget — payees, categories, and accounts will sync automatically

## Tech Stack

| Category | Library | Version |
|---|---|---|
| Language | Kotlin | 2.1.10 |
| UI Framework | Jetpack Compose (BOM) | 2025.02.00 |
| DI | Hilt | 2.54 |
| Database | Room | 2.7.1 |
| Networking | Retrofit | 2.11.0 |
| HTTP Client | OkHttp | 4.12.0 |
| JSON | Moshi | 1.15.2 |
| Camera | CameraX | 1.4.1 |
| OCR | Google ML Kit Text Recognition | 19.0.1 |
| Image Loading | Coil | 3.1.0 |
| Background Work | WorkManager | 2.10.0 |
| Unit Testing | JUnit 5 | 5.11.4 |
| Mocking | MockK | 1.13.16 |
| Flow Testing | Turbine | 1.2.0 |
| Min SDK | Android 13+ | API 33 |
| Target SDK | Android 15 | API 35 |

## Architecture

The project follows **Clean Architecture** with three distinct layers:

```
┌──────────────────────────────────┐
│         Presentation             │
│   (Compose UI, ViewModels)       │
├──────────────────────────────────┤
│           Domain                 │
│   (Use Cases, Repository Interfaces) │
├──────────────────────────────────┤
│            Data                  │
│   (Room, Retrofit, Repositories) │
└──────────────────────────────────┘
```

- **Presentation** — Jetpack Compose screens and ViewModels. Screens include Camera Capture, Transaction Review (with fuzzy payee matching, category suggestions, and account detection), Receipt History, and Settings.
- **Domain** — Pure Kotlin use cases and repository interfaces with no Android framework dependencies.
- **Data** — Repository implementations, Room database, Retrofit API clients, and WorkManager workers.

YNAB API integration targets `api.ynab.com/v1`.

For a detailed architecture breakdown, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Documentation

| Document | Description |
|---|---|
| [BUILDING.md](BUILDING.md) | Build instructions and configuration |
| [TESTING.md](TESTING.md) | Testing strategy and how to run tests |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Detailed architecture and module overview |
| [YNAB_SETUP.md](YNAB_SETUP.md) | YNAB API setup and token configuration |
| [EMULATOR.md](EMULATOR.md) | Emulator setup and testing guide |

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Please ensure all tests pass (`./gradlew test`) and code follows the existing style before submitting.

## License

```
MIT License

Copyright (c) 2025 ReceiptScanner

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
