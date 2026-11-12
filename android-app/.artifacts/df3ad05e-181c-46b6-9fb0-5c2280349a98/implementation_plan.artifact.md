# Implementation Plan - Feature 15: App PIN Gate & Session-Based Unlock

This plan outlines the implementation of Feature 15 (App PIN Gate with session-based unlock, setup, lockout handling, PIN change, and security protections) in PhoneGuard.

## User Review Required

> [!IMPORTANT]
> - **BCrypt Dependency**: We will add `org.mindrot:jbcrypt:0.4` to `app/build.gradle.kts` for secure password/PIN hashing (cost=12).
> - **Session-Based Unlock**: Once unlocked, sessions remain valid for 5 minutes of inactivity. Navigation between Home, Activity, Contacts, and Settings will NOT prompt for PIN within an active session.
> - **Defense-in-Depth Destructive Actions**: Changing PIN requires OLD PIN. Wiping app data requires PIN + typing "DELETE". Logging out requires a confirmation dialog.

## Open Questions

- None. All requirements (15a to 15h) are fully specified.

## Proposed Changes

### Dependencies & Storage

#### [MODIFY] [build.gradle.kts](file:///home/eleazar/Documents/PhoneGuard/android-app/app/build.gradle.kts)
- Add `implementation("org.mindrot:jbcrypt:0.4")` for BCrypt hashing.

#### [MODIFY] [TokenStore.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/data/storage/TokenStore.kt)
- Add methods for storing/retrieving pin failed attempts count, lockout timestamp, first failure timestamp, and auto-wipe on 10 failures preference.

---

### Session & Lifecycle Management

#### [NEW] [AppSessionManager.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/AppSessionManager.kt)
- In-memory singleton (`object AppSessionManager`) managing `isUnlocked`, `lastActivityTime`.
- Methods: `unlock(pin, context)`, `touch()`, `checkExpiry()`, `lock()`, `clear()`.
- Failed attempt handling with lockout logic (3 failures -> 60s, 5 failures -> 5m, 10 failures -> auto-wipe if enabled).
- Logs `tamper_app_pin` event to Room database on failed PIN attempts.

#### [NEW] [AppLifecycleObserver.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/AppLifecycleObserver.kt)
- Uses `androidx.lifecycle.ProcessLifecycleOwner` to detect app background/foreground transitions (`ON_STOP` -> record time leaving; `ON_START` -> check session expiry).

---

### UI Components & Screens

#### [NEW] [PinEntryScreen.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/auth/PinEntryScreen.kt)
- 4-6 digit masked PIN entry (`●●●●`), NumberPassword keyboard.
- Lockout timer countdown display.
- "Log out / Forgot PIN" option to clear session and re-login.

#### [NEW] [PinSetupScreen.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/auth/PinSetupScreen.kt)
- Prompts user after first login to set a 4-6 digit PIN, confirmed twice.
- Hashes with BCrypt cost=12 and stores in `TokenStore`.

#### [MODIFY] [MainActivity.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/MainActivity.kt)
- Set `FLAG_SECURE` on window (prevent screenshots and recent-apps thumbnails).
- Check login state, PIN setup state (`hasAppPin`), and `AppSessionManager.isUnlocked`.
- Show `PinSetupScreen` if logged in but no app PIN set yet.
- Show `PinEntryScreen` if logged in, has app PIN, but session is locked/expired.
- Otherwise show main app screens (Home, Activity, Contacts, Settings) with touch recording on interaction.

---

### Settings & Destructive Actions

#### [MODIFY] [SettingsViewModel.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/settings/SettingsViewModel.kt)
- Add methods for verifying old PIN and updating PIN.
- Add toggle for auto-wipe on 10 failed PIN attempts.
- Add app data wipe method.

#### [MODIFY] [SettingsScreen.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/ui/settings/SettingsScreen.kt)
- Update "Security & Admin" section:
  - Change App PIN dialog (requires OLD PIN, then NEW PIN twice).
  - Wipe App Data dialog (requires PIN + typing "DELETE").
  - Log out confirmation dialog.
  - Auto-wipe toggle on failed attempts.

---

## Verification Plan

### Automated Tests
- Build verification via `gradle_build(":app:assembleDebug")`.

### Manual Verification
- Deploy app to device/emulator.
- Set PIN on first login -> close app -> reopen -> verify PIN required.
- Enter correct PIN -> navigate freely to Settings, Activity, Contacts (no PIN prompts).
- Close app, wait 6 minutes (or test expiry), reopen -> verify PIN required.
- Change app PIN -> verify old PIN required first.
- Enter incorrect PIN 3 times -> verify 60s lockout.
- Test log out confirmation and forgot PIN flow.
- Test app data wipe with PIN + "DELETE".
