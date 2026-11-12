# PhoneGuard Demo Flow Script

This document outlines the step-by-step verification script for demonstrating PhoneGuard end-to-end. Target completion time: **< 5 minutes**.

---

## Prerequisites
- Android device (Pixel 3a class or higher, running Android 9+).
- Device Administrator permission enabled.
- Active internet connection (optional for local demo, required for backend sync).

---

## Step-by-Step Demo Script

### 1. Fresh Install & Account Registration
- **Action:** Install release APK on a fresh device. Open the app.
- **Verification:** Tap **Register**, enter email, password, and name, then submit.
- **Expected Result:** Account created successfully, user is logged in, and lands on the home dashboard.

### 2. Enable Alert Mode & Motion Trigger (Alarm Play)
- **Action:** Toggle **Alert Mode** to **Armed**. Lock the phone (press power button).
- **Verification:** Pick up / shake the phone while locked.
- **Expected Result:** Within seconds, the loud siren alarm plays for 5 seconds (or until grace period triggers).

### 3. Grace Period Unlock & False Alarm Logging
- **Action:** Before the grace period expires, unlock the phone using fingerprint or PIN.
- **Verification:** Check the Activity Log screen inside the app.
- **Expected Result:** Alarm stops immediately. An event of type `false_alarm` is logged in the local database.

### 4. Auto-Lock & Unattended Pickup Handling
- **Action:** Arm Alert Mode again and lock the phone. Pick up the phone and **do not** unlock during the grace period.
- **Verification:** Observe device behavior.
- **Expected Result:** Phone triggers alarm, captures photo via EvidenceCapturer, and automatically locks / forces decoy or lock screen state.

### 5. Wrong PIN Intruder Capture
- **Action:** Open lock screen or enter wrong PIN 5 consecutive times.
- **Verification:** Open app and navigate to **Activity Log**.
- **Expected Result:** Intruder photo is captured via front camera, and a `wrong_pin` event is recorded in the activity list with the captured photo attached.

### 6. SIM Change Detection
- **Action:** Power off device, swap the SIM card with a new one, and power the device back on.
- **Verification:** Unlock device and open PhoneGuard app or check background service logs.
- **Expected Result:** SIM change is detected, SMS alert sent to trusted contacts (if configured), and a `sim_change` event is recorded in Activity Log.

### 7. Activity Log Review with Photos
- **Action:** Navigate to the **Activity** tab in the app.
- **Verification:** Scroll through the list of recorded events (`false_alarm`, `wrong_pin`, `sim_change`, etc.).
- **Expected Result:** All events display correct timestamps, event types, and associated captured intruder/evidence photos.

### 8. Backend Sync (Optional)
- **Action:** Ensure device is connected to the internet with valid Auth token. Trigger manual sync or wait for background SyncWorker.
- **Verification:** Check backend server dashboard or logs.
- **Expected Result:** All local events and captured photos are successfully synced to the backend server.

---
*Target Flow Duration: < 5 minutes.*
