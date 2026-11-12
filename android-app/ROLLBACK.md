# PhoneGuard Rollback & Cleanup Guide

This document describes procedures for cleanly uninstalling PhoneGuard, resetting the local database, and clearing app data.

---

## 1. Clean Uninstall (Removing Device Administrator First)

Because PhoneGuard requests Device Administrator privileges to enforce security policies and prevent unauthorized uninstalls/tampering, **standard uninstallation will fail** unless Device Administrator is first deactivated.

### Method A: Via App Settings (Recommended)
1. Open **PhoneGuard** app.
2. Go to **Settings**.
3. Toggle off **Device Administrator** (or tap "Deactivate Admin").
4. Proceed to standard Android uninstallation (Long-press app icon → **Uninstall**).

### Method B: Via Android System Settings
1. Open Android **Settings**.
2. Search for or navigate to **Security** → **Device Admin apps** (or **Device & app access**).
3. Find **PhoneGuard** and uncheck/deactivate it.
4. Go to Android **Settings** → **Apps** → **PhoneGuard** → Tap **Uninstall**.

---

## 2. Resetting the Local Database

To clear all locally stored events, contacts, and state without uninstalling:

1. Open Android **Settings** → **Apps** → **PhoneGuard**.
2. Tap **Storage & cache**.
3. Tap **Clear Storage** (or **Clear Data**).
*Alternatively, within the app (if developer/debug options or database reset is implemented), use the reset flow or reinstall the app.*

---

## 3. Clearing App Data & Credentials

To completely wipe tokens, encryption keys, and cached credentials stored in EncryptedSharedPreferences/DataStore:

1. Open Android **Settings** → **Apps** → **PhoneGuard**.
2. Tap **Storage & cache**.
3. Tap **Clear Storage** (this deletes all private app data, Room database, DataStore preferences, and Master Keys).
4. Re-opening the app will present a clean registration/login screen.
