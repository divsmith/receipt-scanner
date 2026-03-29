# Project Guidelines

## Architecture

Clean Architecture + MVVM with Jetpack Compose. Three layers enforced by package structure under `com.receiptscanner`:

- `presentation/` — Compose screens + ViewModels, organized by feature (camera, review, history, settings)
- `domain/` — pure Kotlin use cases, repository interfaces, and models; **zero Android framework imports**
- `data/` — Room, Retrofit, ML Kit OCR, WorkManager, and repository implementations
- `di/` — Hilt modules

See [ARCHITECTURE.md](../ARCHITECTURE.md) for full details.

## Build and Test

```bash
# Build debug (must succeed before marking any task complete)
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires running emulator — see EMULATOR.md)
./gradlew connectedDebugAndroidTest
```

> **Validation rule**: A task is not complete until `./gradlew assembleDebug` finishes successfully with no errors. For release changes, use `./gradlew assembleRelease`.

See [BUILDING.md](../BUILDING.md) and [TESTING.md](../TESTING.md) for detailed guidance.

## Conventions

- **Annotation processing**: KSP only — never use kapt or mix the two
- **Async**: Coroutines + Flow throughout; `StateFlow` for UI state, `SharedFlow` for one-off events
- **Dependencies**: All versions managed in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) — add new dependencies there, never inline
- **Room migrations**: Schema exports live in `app/schemas/`; always write explicit migrations, never `fallbackToDestructiveMigration` in production
- **ProGuard**: Enabled on release — add rules to `app/proguard-rules.pro` when adding libraries that require them
- **Skills**: 17 domain skills in `.github/skills/` cover architecture, Compose, testing, data layer, Gradle, and more — load the relevant skill before implementing in its domain

## Documentation

When adding features, updating APIs, or changing architecture:

- **Keep [README.md](../README.md) current** — update the Features list, Tech Stack table, and Architecture section to reflect any user-visible or structural changes introduced by the task
- Link to existing docs ([ARCHITECTURE.md](../ARCHITECTURE.md), [BUILDING.md](../BUILDING.md), [TESTING.md](../TESTING.md), [YNAB_SETUP.md](../YNAB_SETUP.md), [EMULATOR.md](../EMULATOR.md)) rather than duplicating their content
