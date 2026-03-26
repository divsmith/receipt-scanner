# Architecture

## Overview

Receipt Scanner follows **Clean Architecture** with the **MVVM** pattern, organized into three distinct layers:

- **Presentation** — Jetpack Compose UI screens and ViewModels
- **Domain** — Use cases, repository interfaces, and business models
- **Data** — Repository implementations, Room database, Retrofit networking, and ML Kit OCR

Dependencies flow inward: Presentation → Domain ← Data. The domain layer has **zero** Android framework dependencies, making business logic fully unit-testable.

## Layer Diagram

```
┌─────────────────────────────────────────────────────┐
│                  Presentation Layer                   │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌───────┐ │
│  │ Camera  │  │ Review  │  │ History  │  │Settings│ │
│  │ Screen  │  │ Screen  │  │ Screen   │  │Screen  │ │
│  └────┬────┘  └────┬────┘  └────┬─────┘  └───┬───┘ │
│       │            │            │             │      │
│  ┌────┴────┐  ┌────┴────┐  ┌───┴──────┐ ┌───┴───┐  │
│  │ Camera  │  │ Review  │  │ History  │ │Settings│  │
│  │ViewModel│  │ViewModel│  │ViewModel│ │ViewModel│ │
│  └─────────┘  └─────────┘  └──────────┘ └───────┘  │
├─────────────────────────────────────────────────────┤
│                    Domain Layer                       │
│  ┌────────────────────────────────────────────────┐  │
│  │              Use Cases                          │  │
│  │  ExtractReceiptData · MatchPayee · MatchAccount │  │
│  │  SuggestCategory · SubmitTransaction            │  │
│  │  ProcessOfflineQueue · SyncPayeeCache           │  │
│  └────────────────────────────────────────────────┘  │
│  ┌──────────────┐  ┌────────────────────────────┐   │
│  │    Models     │  │  Repository Interfaces     │   │
│  └──────────────┘  └────────────────────────────┘   │
│  ┌──────────────────────────────────────────────┐   │
│  │         Utilities (FuzzyMatcher, Milliunits)  │   │
│  └──────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────┤
│                     Data Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐ │
│  │  Remote   │  │  Local   │  │       OCR          │ │
│  │ YnabApi  │  │Room DB   │  │ MlKit + Parser     │ │
│  │ Retrofit │  │DataStore │  │                     │ │
│  │ OkHttp   │  │Crypto    │  │                     │ │
│  └──────────┘  └──────────┘  └────────────────────┘ │
│  ┌────────────────────────────────────────────────┐  │
│  │           Repository Implementations            │  │
│  └────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

## Data Flow

The complete pipeline from camera capture to YNAB submission:

```
Camera Capture
      │
      ▼
  Image File
      │
      ▼
 ML Kit OCR ──────────► Raw Text
                            │
                            ▼
                     Receipt Parser
                            │
                            ▼
                  ExtractedReceiptData
                   (store, total, date,
                    card last four)
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        Fuzzy Payee    Category      Account
         Match        Suggestion    Detection
              └─────────────┼─────────────┘
                            ▼
                Transaction Review Screen
                     (user edits)
                            │
                            ▼
                   Submit Transaction
                      /          \
                     /            \
                    ▼              ▼
              YNAB API        Offline Queue
             (success)        (no network)
                                   │
                                   ▼
                              WorkManager
                            (retry w/ backoff)
                                   │
                                   ▼
                              YNAB API
```

### Step-by-Step

1. **Camera Capture** — `CameraManager` captures a photo and saves it as a JPEG file.
2. **ML Kit OCR** — `MlKitTextRecognizer` runs Google ML Kit Text Recognition on-device, producing raw text.
3. **Receipt Parser** — `ReceiptParser` extracts structured data (store name, total amount, date, card last four digits) from the raw OCR text using pattern matching.
4. **Fuzzy Payee Match** — `MatchPayeeUseCase` compares the extracted store name against cached YNAB payees using `FuzzyMatcher` (Levenshtein + Jaccard similarity).
5. **Category Suggestion** — `SuggestCategoryUseCase` suggests a category based on the matched payee's transaction history.
6. **Account Detection** — `MatchAccountUseCase` matches the card's last four digits against YNAB account notes.
7. **Transaction Review** — The user reviews and edits the pre-filled transaction on `TransactionReviewScreen`.
8. **Submit** — `SubmitTransactionUseCase` sends the transaction to the YNAB API. If offline, a `PendingTransaction` is enqueued for later submission via `ProcessOfflineQueueUseCase`.

## Package Structure

All source code lives under `app/src/main/java/com/receiptscanner/`.

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| *(root)* | Application entry point | `ReceiptScannerApp` (`@HiltAndroidApp`) |
| `data/camera` | Camera hardware abstraction | `CameraManager` |
| `data/local` | Local persistence (Room, DataStore, encrypted storage) | `AppDatabase`, `TokenProvider`/`TokenProviderImpl`, `UserPreferencesManager` |
| `data/local/dao` | Room Data Access Objects | `AccountCacheDao`, `CategoryCacheDao`, `PayeeCacheDao`, `PendingTransactionDao`, `ReceiptDao`, `SyncMetadataDao` |
| `data/local/entity` | Room entity definitions | `AccountCacheEntity`, `CategoryCacheEntity`, `PayeeCacheEntity`, `PendingTransactionEntity`, `ReceiptEntity`, `SyncMetadataEntity` |
| `data/ocr` | On-device text recognition | `MlKitTextRecognizer`, `ReceiptParser` |
| `data/remote` | YNAB API networking | `YnabApi` (Retrofit interface) |
| `data/remote/dto` | API data transfer objects | `AccountDto`, `BudgetDto`, `CategoryDto`, `PayeeDto`, `TransactionDto`, `YnabResponse` |
| `data/remote/interceptor` | OkHttp interceptors | `AuthInterceptor` (Bearer token injection) |
| `data/repository` | Repository implementations | `YnabRepositoryImpl`, `ReceiptRepositoryImpl`, `TransactionQueueRepositoryImpl` |
| `di` | Hilt dependency injection modules | `AppModule`, `DatabaseModule`, `NetworkModule`, `OcrModule` |
| `domain/model` | Business models | `Account`, `Budget`, `Category`, `CategoryGroup`, `MatchResult`, `Payee`, `PendingTransaction`, `Receipt`, `ExtractedReceiptData`, `Transaction` |
| `domain/repository` | Repository interfaces (ports) | `YnabRepository`, `ReceiptRepository`, `TransactionQueueRepository` |
| `domain/usecase` | Business logic use cases | `ExtractReceiptDataUseCase`, `MatchPayeeUseCase`, `MatchAccountUseCase`, `SuggestCategoryUseCase`, `SubmitTransactionUseCase`, `ProcessOfflineQueueUseCase`, `SyncPayeeCacheUseCase` |
| `domain/util` | Shared domain utilities | `FuzzyMatcher`, `MilliunitConverter` |
| `presentation` | Activity and shared UI | `MainActivity` |
| `presentation/camera` | Camera capture screen | `CameraScreen`, `CameraViewModel` |
| `presentation/components` | Reusable Compose components | `AccountPicker`, `AmountInput`, `CategoryPicker`, `PayeeField`, `ReceiptThumbnail` |
| `presentation/history` | Receipt history screen | `ReceiptHistoryScreen`, `ReceiptHistoryViewModel` |
| `presentation/navigation` | Compose Navigation graph | `AppNavigation` |
| `presentation/review` | Transaction review screen | `TransactionReviewScreen`, `TransactionReviewViewModel` |
| `presentation/settings` | App settings screen | `SettingsScreen`, `SettingsViewModel` |
| `presentation/theme` | Material 3 theming | `Color`, `Theme`, `Type` |

## Key Design Decisions

### 1. On-Device OCR (ML Kit)

- **No cloud dependency** — works fully offline with zero API costs.
- Google ML Kit Text Recognition runs entirely on-device using the device's neural engine.
- `MlKitTextRecognizer` wraps the ML Kit API and returns raw text blocks.
- `ReceiptParser` applies regex-based heuristics to extract structured fields (store name, total, date, card last four) from the raw OCR output.

### 2. Fuzzy Matching over AI Classification

`FuzzyMatcher` uses a deterministic, two-part scoring algorithm:

```
Combined Score = (0.6 × String Similarity) + (0.4 × Token Similarity)
```

- **String Similarity** (60% weight) — Based on Levenshtein edit distance, normalized to a 0.0–1.0 scale.
- **Token Similarity** (40% weight) — Jaccard similarity over tokenized words, handling word reordering (e.g., "Walmart Supercenter" vs. "SUPERCENTER WALMART").
- **Why not ML?** — Deterministic, fast, no model to ship or update, and works reliably for matching receipt store names against YNAB payee names.

### 3. Offline Queue with WorkManager

Transactions submitted without network connectivity are queued for later:

- `PendingTransaction` entities are stored in Room with a `PendingStatus`:
  - `PENDING` — Awaiting submission
  - `SUBMITTING` — Currently being sent
  - `FAILED` — Submission failed, will be retried
- WorkManager schedules retries with **exponential backoff**.
- `ProcessOfflineQueueUseCase` processes the queue, transitioning each item through statuses.
- Transactions are submitted to YNAB when connectivity is restored.

### 4. Delta Sync for YNAB Data

YNAB enforces a **200 requests/hour** rate limit, so efficient syncing is critical:

- `SyncMetadataEntity` stores the `server_knowledge` token per data type (accounts, payees, categories).
- Each sync request includes the last `server_knowledge` value; the API responds with only the changes since that point.
- Synced data is cached in Room via `AccountCacheEntity`, `PayeeCacheEntity`, and `CategoryCacheEntity`.
- `SyncPayeeCacheUseCase` orchestrates the delta sync flow.

### 5. Milliunits Currency Format

YNAB represents all monetary values in **milliunits** (1 dollar = 1,000 milliunits):

```
$45.99  →  45990 milliunits
-$12.50 → -12500 milliunits (outflow)
```

- `MilliunitConverter` handles all conversions between display format and milliunits.
- Outflows are represented as **negative** values.
- Using integer milliunits eliminates floating-point precision issues entirely.

### 6. Secure Token Storage

The YNAB personal access token is stored securely:

- **AndroidX Security Crypto** (`EncryptedSharedPreferences`) encrypts the token at rest using AES-256.
- `TokenProvider` interface decouples token retrieval from the storage mechanism, enabling easy testing with fakes.
- `AuthInterceptor` (OkHttp interceptor) reads from `TokenProvider` and injects the `Bearer` token into every API request automatically.

## Dependency Injection

Hilt provides compile-time DI across four modules:

### `AppModule`

Binds application-level dependencies:

- `TokenProvider` → `TokenProviderImpl` (EncryptedSharedPreferences-backed)

### `DatabaseModule`

Provides Room database and all DAOs:

- `AppDatabase` — Room database named `"receipt_scanner.db"`
- All 6 DAOs: `AccountCacheDao`, `CategoryCacheDao`, `PayeeCacheDao`, `PendingTransactionDao`, `ReceiptDao`, `SyncMetadataDao`
- Repository bindings:
  - `YnabRepository` → `YnabRepositoryImpl`
  - `ReceiptRepository` → `ReceiptRepositoryImpl`
  - `TransactionQueueRepository` → `TransactionQueueRepositoryImpl`

### `NetworkModule`

Provides the full networking stack:

- **Moshi** — JSON serialization/deserialization
- **OkHttpClient** — 30-second connect/read/write timeouts, `AuthInterceptor`, body-level `HttpLoggingInterceptor`
- **Retrofit** — Base URL: `https://api.ynab.com/v1/`, Moshi converter
- **`YnabApi`** — Retrofit service interface

### `OcrModule`

Provides OCR components:

- `MlKitTextRecognizer` — ML Kit Text Recognition wrapper
- `ReceiptParser` — Structured data extractor

## Testing Strategy

> See [TESTING.md](TESTING.md) for the full testing guide.

The architecture is designed for testability at every layer:

- **Unit Tests** — Domain use cases, `FuzzyMatcher`, `ReceiptParser`, `MilliunitConverter`, and ViewModels with fake repositories.
- **Integration Tests** — API layer with `MockWebServer`, repository implementations with in-memory Room databases.
- **Instrumented Tests** — Compose UI tests for screens and components, navigation tests with `TestNavHostController`.
- **All ViewModels** are fully testable via constructor-injected dependencies — no Android framework mocking needed.
