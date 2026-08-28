# Autopilot

Native Kotlin Android source for the Autopilot screen-analysis assistant.

## Open in Android Studio

Open the `android` directory as an existing Gradle project. Android Studio will
download the declared Gradle and Android dependencies, then the project can be
run on an Android 8.0+ device or emulator.

## Build

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Runtime permissions

Autopilot intentionally uses Android's permission flows:

1. Screen capture permission is requested through `MediaProjectionManager` when
   START is pressed.
2. The user must enable Autopilot in Android Accessibility settings before an
   authorized gesture can be dispatched.
3. The optional floating panel uses the system overlay permission.

The app does not bypass permission dialogs, inspect credentials, or click
outside a validated OCR target. A low-confidence result never triggers an
action.
