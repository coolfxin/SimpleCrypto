# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SimpleCrypto is an Android file encryption/decryption application. Users can select images or videos, encrypt them using AES-256-GCM, and optionally delete the originals. Encrypted files can later be decrypted back to their original locations.

**Package:** `com.coolfxin.simplecrypto`

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Install debug APK to connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

## Architecture

The app follows MVVM architecture with Jetpack Compose:

### Core Components

- **MainActivity.kt** - Entry point, sets up Compose content
- **FileEncryptionScreen.kt** - Main UI (Compose), handles file selection, permissions, displays encrypted files list
- **FileEncryptionViewModel.kt** - Business logic, state management, coordinates encryption/decryption operations
- **CryptoUtils.kt** - AES-256-GCM encryption/decryption using Android Keystore
- **FileUtils.kt** - File operations (URI handling, cache management, deletion)

### Data Flow

1. User selects file via UI → FilePicker
2. ViewModel resolves file path (original or cached copy)
3. Encryption: CryptoUtils encrypts file → saves to `getEncryptedFilesDir()` → original deleted
4. Decryption: CryptoUtils decrypts file → restores to original path → encrypted file deleted

### Key Implementation Details

**Encryption Format (CryptoUtils.kt):**
- AES-256-GCM with Android Keystore (alias: `FILE_ENCRYPTION_KEY`)
- Encrypted file structure: `[IV_LENGTH (1 byte)][IV][ENCRYPTED_DATA]`
- Buffer size: 8192 bytes for streaming operations

**Scoped Storage Handling (Android 13+):**
- Uses `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` permissions
- If original file path is inaccessible, copies to cache before encryption
- Temporary cache files are tracked in ViewModel and cleaned up after operations

**Encrypted Files Storage:**
- Located in `context.getExternalFilesDir(null)` with subdirectory
- Files have `.enc` extension appended to original name

## Version Catalog

Dependencies are managed via `gradle/libs.versions.toml`:
- AGP: 8.13.1
- Kotlin: 2.0.21
- Compose BOM: 2024.09.00
- Target SDK: 36, Min SDK: 33
- Java: 11

## Testing

- Unit tests: `app/src/test/`
- Instrumented tests: `app/src/androidTest/`
- Current tests are placeholder examples - no custom test implementation exists yet
