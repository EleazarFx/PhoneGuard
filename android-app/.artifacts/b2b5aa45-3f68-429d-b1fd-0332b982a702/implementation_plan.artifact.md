# Implementation Plan — Feature 13: Cross-cutting Quality Improvements

Comprehensive cross-cutting quality improvements across Android app and backend server, including unit tests, backend integration tests, structured logging with Timber, local crash reporting with consent, About screen, and ProGuard / R8 optimization.

## User Review Required

> [!IMPORTANT]
> - **Unit Testing Stack**: JUnit 5 + MockK will be configured for Android unit tests (`app/src/test`).
> - **Backend Integration Tests**: Ktor `testApplication` tests in the backend project (`backend/phoneguard-backend/src/test`).
> - **Crash Reporting**: Local crash handler saves reports to `filesDir/crashes/` and triggers email intent, guarded by user consent in Settings ("Send crash reports").
> - **Structured Logging**: Timber integration across Android app, redacting sensitive fields (tokens, passwords, OTPs, full photo paths).

## Open Questions

None. Requirements are clear.

## Proposed Changes

---

### [Android App] Unit Tests (13a)
Add JUnit 5 and MockK dependencies, and write unit tests for:
- `PasswordResetService` / `NewDeviceService.verifyCodeAndRegisterDevice()` (or auth reset validation)
- `SnatchGuard` state transitions (mocking `DevicePolicyManager`, grace period timer, alarm, notification)
- `AlertModeManager` threshold logic (mocking `SensorEvent` with linear acceleration and raw accelerometer, magnitude calculations)
- `SyncManager` sync retry behavior (mocking API responses for success/failure)

#### [MODIFY] [build.gradle.kts](file:///home/eleazar/Documents/PhoneGuard/android-app/app/build.gradle.kts)
#### [MODIFY] [libs.versions.toml](file:///home/eleazar/Documents/PhoneGuard/android-app/gradle/libs.versions.toml)
#### [NEW] [SnatchGuardTest.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/test/java/mw/phoneguard/app/services/SnatchGuardTest.kt)
#### [NEW] [AlertModeManagerTest.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/test/java/mw/phoneguard/app/services/AlertModeManagerTest.kt)
#### [NEW] [SyncManagerTest.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/test/java/mw/phoneguard/app/data/sync/SyncManagerTest.kt)

---

### [Backend] Integration Tests (13b)
Add Ktor `testApplication` tests in backend (`backend/phoneguard-backend/src/test`) for:
- `POST /auth/register` validation
- `POST /auth/login` returning tokens
- Device ownership checks on all `/devices/{id}/*` routes
- Verifying device token cannot create commands (403)
- Password reset flow end-to-end with mock SMTP (`EmailService`)

#### [MODIFY] [build.gradle.kts](file:///home/eleazar/Documents/PhoneGuard/backend/phoneguard-backend/build.gradle.kts)
#### [NEW] [BackendIntegrationTest.kt](file:///home/eleazar/Documents/PhoneGuard/backend/phoneguard-backend/src/test/kotlin/mw/phoneguard/BackendIntegrationTest.kt)

---

### [Android App] Structured Logging (13c)
Integrate Timber (`com.jakewharton.timber:timber`):
- Initialize Timber in `PhoneGuardApplication` (`DebugTree` for debug builds, custom release tree or production tree hiding DEBUG, showing INFO+ only).
- Replace ad-hoc `Log.d` with `Timber.d`, `Timber.i`, `Timber.w`, `Timber.e`, tagging every log with `PhoneGuardService`, component tag, and severity.
- Ensure strict redaction of tokens, passwords, OTPs, and full photo paths.

#### [MODIFY] [build.gradle.kts](file:///home/eleazar/Documents/PhoneGuard/android-app/app/build.gradle.kts)
#### [MODIFY] [PhoneGuardApplication.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/PhoneGuardApplication.kt)
#### [MODIFY] Services and ViewModels to use Timber.

---

### [Android App] Crash Reporting & Consent (13d)
Implement local CrashHandler:
- Catch unhandled exceptions (`Thread.setDefaultUncaughtExceptionHandler`).
- Save crash report to `filesDir/crashes/crash_<timestamp>.txt`.
- Check user consent ("Send crash reports" setting in `TokenStore` / `SettingsViewModel`).
- If consented, launch intent-based email with attached crash report.

#### [NEW] [CrashHandler.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/CrashHandler.kt)
#### [MODIFY] [TokenStore.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/data/storage/TokenStore.kt)
#### [MODIFY] [SettingsScreen.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/settings/SettingsScreen.kt)
#### [MODIFY] [SettingsViewModel.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/settings/SettingsViewModel.kt)
#### [MODIFY] [PhoneGuardApplication.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/PhoneGuardApplication.kt)

---

### [Android App] About Screen (13e)
Add Settings → About screen:
- App version (`BuildConfig.VERSION_NAME` / `VERSION_CODE`)
- Backend URL (for debugging)
- Device ID (for support)
- "What PhoneGuard cannot do" (honest limitation list from spec Section 2.2)
- Build type (`BuildConfig.BUILD_TYPE`)
- Link to developer email for feedback (`Intent.ACTION_SENDTO` `mailto:`)

#### [MODIFY] [SettingsScreen.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/settings/SettingsScreen.kt)

---

### [Android App] ProGuard / R8 for Release (13f)
Ensure release builds have proper ProGuard rules:
- ProGuard rules for Ktor, Room, kotlinx.serialization, ML Kit in `app/proguard-rules.pro` (or `rules.keep`).
- Configure release build type in `app/build.gradle.kts` (`isMinifyEnabled = true`, `isShrinkResources = true`).

#### [MODIFY] [app/build.gradle.kts](file:///home/eleazar/Documents/PhoneGuard/android-app/app/build.gradle.kts)
#### [MODIFY] [proguard-rules.pro](file:///home/eleazar/Documents/PhoneGuard/android-app/app/proguard-rules.pro)

## Verification Plan

### Automated Tests
- Run Android unit tests: `./gradlew testDebugUnitTest`
- Run backend integration tests: `cd /home/eleazar/Documents/PhoneGuard/backend/phoneguard-backend && ./gradlew test`

### Manual Verification
- Install release build (`./gradlew assembleRelease`), verify installation and launch.
- Verify crash reporting by triggering an unhandled exception or checking CrashHandler.
- Verify Settings → About screen contents and email intent link.
- Verify log output uses Timber and filters release logs correctly.
