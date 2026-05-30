# Copilot instructions for NotiTrace

## Build, test, and lint commands

This repository is a single-module Android app (`:app`) built with Gradle Kotlin DSL.

```bash
# Debug build
./gradlew assembleDebug

# Release build
# Requires NOTITRACE_ANDROID_JKS_PATH / _PASSWORD / _ALIAS / _KEY_PASSWORD
./gradlew assembleRelease

# JVM unit tests
./gradlew :app:testDebugUnitTest

# Run a single unit test class
./gradlew :app:testDebugUnitTest --tests "org.ukky.notitrace.util.SignatureGeneratorTest"

# Connected device / emulator tests
./gradlew :app:connectedDebugAndroidTest

# Android lint
./gradlew :app:lintDebug
```

## High-level architecture

- `MainActivity` is the single activity. It chooses `onboarding` vs `home` as the start destination based on whether `NotificationListenerService` permission is enabled.
- The core capture flow is: `NotiTraceListenerService.onNotificationPosted()` -> `NotificationExtractor.extract()` -> `NotificationRepository.save()` -> `NotificationDao.insert()` plus `NotificationRawLogDao.insert()`.
- The app uses Compose + ViewModel + Repository + Room. ViewModels expose `StateFlow<UiState>`, and screens collect with `collectAsStateWithLifecycle()`.
- Dependency injection is Hilt-based. Room, DAOs, repositories, and ViewModels are wired in `app/src/main/java/org/ukky/notitrace/di/AppModule.kt`, `NotiTraceApplication.kt`, and `MainActivity.kt`.
- The Room database is encrypted with SQLCipher. `DatabaseProvider` creates or restores the passphrase via Android Keystore, then opens `notitrace.db`.
- Search is intentionally two-stage: FTS first, then escaped `LIKE` fallback when FTS returns no rows or fails. Preserve that behavior for short Japanese queries and wildcard-heavy inputs.
- Settings owns the import/export flows. `BackupManager` handles encrypted backup import/export, while `JsonlExporter` writes plain UTF-8 JSONL for both notification data and per-receipt raw logs.

## Key conventions

- **Do not deduplicate notifications by signature.** `NotificationEntity.signature` is metadata for correlation/search only. Since DB v6 it is indexed but not unique, and each receipt is stored as a separate row.
- **Raw notification payloads are stored twice for different purposes.** `notifications.raw_json` keeps the OS-derived JSON on the main record, and `notification_raw_logs` keeps one row per receipt for chronological raw-log export and retention cleanup.
- **Tags are package-level metadata, not copied into notifications.** Store tags through `AppTagRepository` / `AppTagDao`; notification queries join tags at read time so tag edits affect past logs immediately.
- **When touching search, keep the FTS + fallback contract intact.** `NotificationRepositoryImpl.search()` must escape `%`, `_`, and `\` for `LIKE`, and tests cover fallback on zero-hit and MATCH errors.
- **When touching backup/import, preserve current semantics.** Import restores notifications and tags, but intentionally does not restore historical `rawLogs`; new raw-log rows are recreated through normal saves.
- **User-facing copy, comments, and many test names are Japanese.** Match the existing language and tone instead of rewriting nearby strings into English.
- **ViewModel state follows a consistent Flow pattern.** Most screens combine repository flows and local `MutableStateFlow`s, then expose a `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ...)` state object.
- **Keep the AboutLibraries build workaround in mind.** `app/build.gradle.kts` has a custom `GenerateAboutLibrariesResTask` because the plugin does not detect AGP 9.x resources correctly; do not remove it as dead code.
- **Privacy-first constraints are hard requirements.** The app is intentionally offline-only and built around `NotificationListenerService`; avoid adding network paths, cloud sync, or alternate capture mechanisms that break that model.
- **Keep design docs in sync with implementation.** When you implement or change a feature, update `docs/BASIC_DESIGN.md` (not `DESIGN.md`) and any affected `docs/sequence-*.md` diagrams in the same change.

## Android MCP server guidance

- Prefer Android MCP tools when validating notification capture, onboarding, permissions, or Compose UI flows on a device/emulator.
- For UI work, start with an annotated screenshot or UI hierarchy dump, then use element-based taps instead of raw coordinates whenever possible.
- For runtime debugging, use logcat, current-activity, package-info, crash-log, and memory tools before changing app code.
- When testing the main capture path, confirm notification-listener permission is enabled before assuming the listener, extractor, or repository is broken.
