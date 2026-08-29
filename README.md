# Autopilot

Native Kotlin Android source for the Autopilot screen-analysis assistant.

## Open in Android Studio

Open the repository root as an existing Gradle project. The Android module is
at `app/` (the original nested `aalam-repo/app` location has been flattened).
Use JDK 17 with Android Studio or the command line. Android Studio will download
the declared Gradle and Android dependencies, then the project can be run on an
Android 8.0+ device or emulator.

## Build

From the repository root, run:

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The release build enables R8/resource shrinking. Validate it with:

```bash
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lint
```

## Runtime permissions

Autopilot intentionally uses Android's permission flows:

1. Screen capture permission is requested through `MediaProjectionManager` when
   START is pressed.
2. The user must enable Autopilot in Android Accessibility settings before an
   authorized gesture can be dispatched.
3. The optional floating panel uses the system overlay permission.

The app does not bypass permission dialogs, inspect credentials, or click
outside a validated OCR target. A low-confidence result never triggers an
action. Screen frames are processed in memory and are not intentionally
persisted. Monitoring stops when the service is stopped or the process is
terminated; it does not silently resume after process death,
