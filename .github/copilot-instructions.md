# Project Guidelines

## Project Scope

Receipt Scanner is an Android app for YNAB users. The core user flow is:

1. Capture or import a receipt image
2. Run on-device OCR
3. Parse structured transaction fields
4. Suggest payee/category/account
5. Submit to YNAB or enqueue for offline retry

When implementing features, preserve this offline-first behavior and deterministic parsing/matching pipeline.

## Architecture

Clean Architecture + MVVM with Jetpack Compose. Layers are enforced by package structure under `com.receiptscanner`:

- `presentation/` - Compose UI, navigation, and ViewModels (feature folders: camera, review, history, settings)
- `domain/` - pure Kotlin models, repository interfaces, and use cases; **zero Android framework imports**
- `data/` - repository implementations, Room, Retrofit/OkHttp, ML Kit OCR, DataStore/security, WorkManager
- `di/` - Hilt modules wiring app, database, network, and OCR dependencies

Dependency direction is always inward: `presentation -> domain <- data`.
Do not let `presentation` depend directly on `data` classes.

See [ARCHITECTURE.md](../ARCHITECTURE.md) for full diagrams, data flow, and package-level details.

## Core Runtime Behavior

Keep these system behaviors intact unless the task explicitly changes them:

- OCR is on-device (ML Kit), no cloud OCR fallback
- Parsing and matching are deterministic (regex parsing + fuzzy scoring), not LLM-driven
- YNAB values are milliunits and purchase outflows are negative
- Failed transaction submission uses the offline queue and WorkManager retry path


## Build and Validation

Use these commands during implementation:

```bash
# Required completion gate for normal development tasks
./gradlew assembleDebug

# Unit tests (JVM)
./gradlew testDebugUnitTest

# Instrumented tests (requires emulator/device)
./gradlew connectedDebugAndroidTest

# Optional but recommended for broader quality checks
./gradlew lint
```

Validation rule: A task is not complete until `./gradlew assembleDebug` succeeds with no errors.
For release-impacting changes, also run `./gradlew assembleRelease`.

See [BUILDING.md](../BUILDING.md) and [TESTING.md](../TESTING.md) for detailed guidance.

## Conventions

- **Annotation processing**: KSP only. Never add kapt or mix kapt/ksp.
- **Async and state**: Coroutines + Flow. Use `StateFlow` for durable UI state and `SharedFlow` for one-off events.
- **Dependency management**: Declare versions in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml), not inline in module build files.
- **Database evolution**: Room schemas are exported to `app/schemas/`; add explicit migrations and do not use `fallbackToDestructiveMigration` in production.
- **YNAB math**: Use milliunits consistently (1 dollar = 1000 milliunits). Keep purchase outflows negative.
- **Offline reliability**: Preserve pending transaction queue semantics and WorkManager retry behavior.
- **Security**: Treat YNAB tokens as secrets. Do not log them or hardcode credentials.
- **Release safety**: ProGuard/R8 is enabled for release. Add keep rules in `app/proguard-rules.pro` when required by new libraries.
- **Skill usage**: Domain-specific skills are in `.github/skills/`; load the relevant one before making non-trivial changes in that domain.

## Implementation Boundaries

- Put business logic in domain use cases, not Composables.
- Keep Android framework types out of domain models/use cases.
- Prefer repository interface changes in domain first, then implementation in data.
- Follow existing feature packaging in presentation (`camera`, `review`, `history`, `settings`) when adding screens or ViewModels.
- Reuse existing DI modules in `di/`; avoid ad-hoc service locators or manual singletons.

## Documentation

When adding features, updating APIs, or changing architecture:

- **Keep [README.md](../README.md) current** - update Features, Tech Stack, and Architecture sections for user-visible or structural changes
- Link to existing docs ([ARCHITECTURE.md](../ARCHITECTURE.md), [BUILDING.md](../BUILDING.md), [TESTING.md](../TESTING.md), [YNAB_SETUP.md](../YNAB_SETUP.md), [EMULATOR.md](../EMULATOR.md)) rather than duplicating their content
