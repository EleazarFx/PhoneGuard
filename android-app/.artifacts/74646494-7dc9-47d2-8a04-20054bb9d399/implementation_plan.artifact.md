# PhoneGuard Logic Fixes, Feature Enhancements & Optimization Plan

This implementation plan outlines the fixes and enhancements for the PhoneGuard Android application and backend, addressing all reported issues with robust Android best practices.

## User Review Required

> [!IMPORTANT]
> **Email Escalation:** Implemented server-side in the backend (`DeviceService` / `EmailService`) so that whenever a critical event (such as `sim_change`, `snatch`, or `tamper`) is posted by the device, the backend instantly emails the registered owner.
>
> **Thief Countermeasures (Turning off / Factory Reset):**
> - **Power-Off / Shutdown Protection:** Addressed via the **Decoy Shutdown** mechanism (`DecoyShutdownActivity`). When activated, it simulates a hyper-realistic shutdown screen while keeping the phone fully awake in the background to track GPS, capture thief photos, and sync evidence.
> - **Factory Reset:** While Android restricts blocking hardware recovery wipes without MDM/Device Owner, Google's built-in **Factory Reset Protection (FRP)** ensures the device remains bricked/locked to the owner's Google account credentials.

## Proposed Changes

### Android App (`/app`)

#### [MODIFY] [LocationHelper.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/LocationHelper.kt)
- Upgrade location retrieval from static `getLastKnownLocation` to active `requestSingleUpdate` with fallback and timeout so `lat`/`lng` are never null when GPS/Network providers are enabled.

#### [MODIFY] [EvidenceCapturer.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/EvidenceCapturer.kt)
- Add a lightweight `captureLocationAndBattery()` method for periodic location pings (so periodic pings do **not** capture photos).
- Ensure tamper and snatch events (`no_face` or `camera_blocked`) successfully attach metadata and sync rather than being dropped.
- Implement working CameraX video capture when video evidence is enabled.

#### [MODIFY] [PhoneGuardService.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/PhoneGuardService.kt)
- Use lightweight location/battery capture for periodic location pings.
- Ensure screen-off and locked state correctly maintain alert mode monitoring.

#### [MODIFY] [HomeScreen.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/home/HomeScreen.kt)
- Wire up "Test Decoy Shutdown" button to successfully launch `DecoyShutdownActivity`.

#### [MODIFY] [SettingsScreen.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/settings/SettingsScreen.kt)
- Standardize toggle buttons to match Home screen styling.
- Add clear instructions for obtaining IMEI (dialing `*#06#` or checking About Phone).
- Gate Debug & Diagnostics section behind `BuildConfig.DEBUG` so it is hidden in production.

---

### Backend (`/backend/phoneguard-backend`)

#### [MODIFY] [DeviceService.kt](file:///home/eleazar/Documents/PhoneGuard/backend/phoneguard-backend/bin/main/mw/phoneguard/services/DeviceService.kt)
- Add server-side email escalation when critical security events (`sim_change`, `snatch`, `tamper`, `wrong_pin`) are received, emailing the account owner via `EmailService`.

## Verification Plan

### Automated Tests
- Run Gradle unit tests (`./gradlew testDebugUnitTest`).
- Build verification (`./gradlew :app:assembleDebug`).

### Manual Verification
- Deploy to emulator/device, verify periodic location pings, test decoy shutdown, verify toggle styling, and check debug section visibility in release builds.
