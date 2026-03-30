# Testing

## Overview

This project follows a layered testing strategy. **Unit tests** run on the JVM and cover business logic including OCR parsing, API interactions, repository operations, use cases, and matching algorithms. **Integration tests** verify API client behavior using MockWebServer. **Instrumented tests** run on a physical device or emulator and cover UI interactions via Jetpack Compose testing and end-to-end workflows.

## Test Architecture

```
Unit Tests (JVM)              Instrumented Tests (Device/Emulator)
├── OCR Parser                ├── UI Tests (Compose)
├── API Client                ├── Integration Tests
├── Repositories              └── End-to-End Tests
├── Use Cases
├── Matching Algorithms
└── Utilities
```

## Running Tests

### Unit Tests

```bash
./gradlew testDebugUnitTest
```

Reports at: `app/build/reports/tests/testDebugUnitTest/`

### Instrumented Tests

```bash
./gradlew connectedDebugAndroidTest
```

Requires a connected device or running emulator. Reports at: `app/build/reports/androidTests/connected/`

### OCR Fixture Regression

Labelled OCR fixtures live under `app/src/test/resources/images/`, with expectations in
`app/src/test/resources/images/labels.md`.

Run the fixture-backed OCR regression on a connected device or emulator with:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.receiptscanner.data.ocr.OcrFixtureRegressionTest#extractedDataMatchesLabelledReceipts
```

The suite exercises the real on-device OCR pipeline end to end:

1. Load receipt images from test assets
2. Run ML Kit text recognition on-device
3. Parse store, total, date, and card-last-four
4. Compare extracted data to the labelled expectations
5. Fail only if baseline accuracy thresholds regress

Current baseline gates:

- store accuracy >= 60%
- total accuracy >= 65%
- date accuracy >= 65%
- card last-four accuracy >= 75%
- exact record accuracy >= 20%

On failure, the test prints an OCR fixture scorecard with per-image mismatches so parser and
normalization regressions can be tuned incrementally.

#### Dumping OCR Results for Offline Iteration

The on-device test can serialize ML Kit's raw OCR output to JSON files. This lets you iterate
on `ReceiptParser` heuristics using the fast JVM-only test (seconds) instead of re-running the
full on-device pipeline (minutes).

**Step 1 — Dump OCR results from device:**

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.receiptscanner.data.ocr.OcrFixtureRegressionTest#extractedDataMatchesLabelledReceipts \
  -Pandroid.testInstrumentationRunnerArguments.ocrFixture.dumpOcrResults=true \
  -Pandroid.testInstrumentationRunnerArguments.ocrFixture.enforceThresholds=false
```

**Step 2 — Pull OCR cache to your machine:**

```bash
adb pull /sdcard/Android/data/com.receiptscanner/files/ocr-cache/ app/src/test/resources/ocr-cache/
```

**Step 3 — Iterate on parser heuristics using the JVM test:**

```bash
./gradlew testDebugUnitTest --tests "com.receiptscanner.data.ocr.ReceiptParserFixtureTest"
```

This test replays the cached ML Kit output through `ReceiptParser` and prints a scorecard.
Change parser logic → re-run → check scores → repeat. No emulator required.

Re-dump OCR results (Step 1) only when you change image preprocessing.

### All Tests

```bash
./gradlew test connectedCheck
```

### Specific Test Class

```bash
./gradlew testDebugUnitTest --tests "com.receiptscanner.data.ocr.ReceiptParserTest"
```

## Test Coverage

| Area | Test File | Tests | Description |
|------|-----------|-------|-------------|
| OCR Parser | `ReceiptParserTest` | 28 | Receipt text extraction, date/amount/store parsing |
| API Client | `YnabApiTest` | 12 | YNAB API request/response, error handling |
| Repositories | `YnabRepositoryImplTest` | 14 | Data layer, caching, sync logic |
| Matching Algorithms | `FuzzyMatcherTest` | 19 | Levenshtein distance, token similarity, combined scoring |
| Account Matching | `MatchAccountUseCaseTest` | — | Card last-4 matching, account selection |
| Payee Matching | `MatchPayeeUseCaseTest` | — | Fuzzy payee name matching |
| Category Suggestions | `SuggestCategoryUseCaseTest` | — | History-based category suggestions |
| Utilities | `MilliunitConverterTest` | 9 | Dollar/cent to milliunit conversion |
| **Total** | | **82+** | |

## Writing New Tests

### Conventions

- Test files mirror the source structure under `src/test/`
- Test class naming: `{ClassName}Test`
- Use `@Test` annotation (JUnit 5)
- Use `@DisplayName` for readable test names
- Use `@Nested` for grouping related tests
- Use `@ParameterizedTest` with `@ValueSource` or `@CsvSource` for data-driven tests

### Example Unit Test

```kotlin
import io.mockk.coEvery
import io.mockk.mockk
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MyUseCaseTest {

    private val repository = mockk<MyRepository>()
    private val useCase = MyUseCase(repository)

    @Nested
    @DisplayName("when repository returns data")
    inner class WhenDataAvailable {

        @Test
        @DisplayName("should emit mapped result")
        fun emitsMappedResult() = runTest {
            coEvery { repository.getData() } returns flowOf(listOf(item))

            useCase().test {
                assertEquals(expected, awaitItem())
                awaitComplete()
            }
        }
    }
}
```

### Frameworks Reference

| Framework | Purpose | Key APIs |
|-----------|---------|----------|
| JUnit 5 (5.11.4) | Test framework | `@Test`, `@Nested`, `@ParameterizedTest`, `Assertions.*` |
| MockK (1.13.16) | Mocking | `mockk()`, `every {}`, `coEvery {}`, `verify {}`, `slot()` |
| Turbine (1.2.0) | Flow testing | `flow.test {}`, `awaitItem()`, `awaitComplete()` |
| Coroutines Test (1.10.1) | Coroutine testing | `runTest {}`, `TestDispatcher`, `advanceUntilIdle()` |
| MockWebServer (4.12.0) | HTTP mocking | `MockWebServer`, `MockResponse`, `enqueue()` |
| AndroidX Test (1.6.1) | Android test utilities | `ApplicationProvider`, `ActivityScenarioRule` |
| Espresso (3.6.1) | UI testing | `onView()`, `perform()`, `check()` |
| Compose UI Test | Compose UI testing | `createComposeRule()`, `onNodeWithText()`, `performClick()` |
| Hilt Testing (2.54) | DI in tests | `@HiltAndroidTest`, `HiltAndroidRule` |
| Arch Core Testing (2.2.0) | LiveData testing | `InstantTaskExecutorRule` |

## Continuous Integration

### CI Pipeline (`ci.yml`)

Triggered on push to `main`/`develop` and pull requests to `main`.

**Steps:**
1. Checkout repository
2. Set up JDK 17
3. Configure Gradle
4. Run `./gradlew lint`
5. Run `./gradlew testDebugUnitTest`
6. Build debug APK

**Artifacts:** lint results, test reports, debug APK

### Instrumented Tests Pipeline (`instrumented-tests.yml`)

Triggered on pull requests to `main`.

**Steps:**
1. Checkout repository
2. Set up JDK 17
3. Configure Gradle
4. Enable KVM acceleration
5. Start emulator (API 33, `x86_64`, `google_apis`)
6. Run `./gradlew connectedDebugAndroidTest`

**Timeout:** 30 minutes
**Artifacts:** instrumented test results
