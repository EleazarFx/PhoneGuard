# Implementation Plan - Feature 7: Command Polling & Execution

Implement remote command polling and execution for PhoneGuard, supporting backend communication (`GET /device/{deviceId}/commands` and `POST /device/{deviceId}/commands/{commandId}/status`), secure execution of commands (`LOCK`, `ALARM`, `MESSAGE`, `LOCATE`, `LIVE_TRACK`, `WIPE`, and unknown), and WorkManager-based adaptive polling (every 60s when locked, every 15 min when active).

## User Review Required

> [!IMPORTANT]
> - **WIPE Execution**: Requires both Device Admin active and `wipe_allowed` (`remoteWipeEnabled`) set to `true` in `TokenStore` (controlled via Settings).
> - **Polling Frequency**: Uses WorkManager `OneTimeWorkRequest` with 60s delay when screen/device is locked, and `PeriodicWorkRequest` (15 min interval) when active/unlocked.

## Proposed Changes

### Data Models & API (`mw.phoneguard.app.data.models` & `mw.phoneguard.app.data.api`)

#### [NEW] [Commands.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/data/models/Commands.kt)
- Define `@Serializable` data classes `CommandResponse` (`id`, `type`, `params`, `duration`, `message`, `minutes`, `expiresAt`, `createdAt`) and `CommandStatusRequest` (`status`, `message`, `timestamp`).

#### [MODIFY] [DeviceApi.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/data/api/DeviceApi.kt)
- Add `getCommands(deviceToken: String, deviceId: String): ApiResult<List<CommandResponse>>`
- Add `postCommandStatus(deviceToken: String, deviceId: String, commandId: String, status: String, message: String? = null): ApiResult<Unit>`

---

### Command Execution & Workers (`mw.phoneguard.app.services`)

#### [NEW] [CommandExecutor.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/CommandExecutor.kt)
- Handle execution logic for each command type:
  1. `LOCK`: `DevicePolicyManager.lockNow()` (checks device admin).
  2. `ALARM`: Play alarm using `AlarmPlayer` for `duration` seconds (default 30s), then stop.
  3. `MESSAGE`: Show full-screen notification / overlay with message text.
  4. `LOCATE`: Read location using `EvidenceCapturer` / location services, send location heartbeat (`postEvent` with `location_ping` or location event).
  5. `LIVE_TRACK`: For `minutes` (default 5 min), send location every 30s.
  6. `WIPE`: Call `dpm.wipeData(0)` (requires device admin and `TokenStore.isRemoteWipeEnabled()`).
  7. Unknown command: report `failed: unknown_command`.

#### [NEW] [CommandPollWorker.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/CommandPollWorker.kt)
- CoroutineWorker that:
  - Fetches pending commands via `DeviceApi`.
  - Checks expiration (`expiresAt`): if expired, reports `expired` and skips.
  - Reports `delivered` immediately upon fetching.
  - Executes command via `CommandExecutor`.
  - Reports `executed` on success or `failed` with error message on failure.
  - If screen is still locked, enqueues the next 60s `OneTimeWorkRequest`.

#### [MODIFY] [PhoneGuardService.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/services/PhoneGuardService.kt)
- Schedule WorkManager command polling when service starts, screen locks (60s polling), or screen unlocks (15 min periodic polling).

---

### Settings & TokenStore

#### [MODIFY] [TokenStore.kt](file:///home/eleazar/Documents/PhoneGuard/android-app/app/src/main/java/mw/phoneguard/app/data/storage/TokenStore.kt)
- Ensure `isRemoteWipeEnabled()` / `saveRemoteWipeEnabled()` supports `wipe_allowed` flag check for WIPE command.

## Verification Plan

### Automated Tests
- Build project successfully using `./gradlew app:assembleDebug`.

### Manual Verification
- Deploy to device/emulator (`./gradlew app:installDebug`).
- Verify command polling and execution via backend simulation or logcat inspection.
