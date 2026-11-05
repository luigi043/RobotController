# Robot Controller

An Android controller for a paired Classic Bluetooth robot using newline-delimited RFCOMM serial commands. The project uses Kotlin, Android Views, Material Components and coroutines. Its scope is robot prototyping and demonstrations. Hardware compatibility and production readiness have not been established.

The Android app includes an explicit **offline demo**, using an in-memory transport. The separate [phone-style browser demo](docs/demo/index.html) illustrates controls and robot movement in JavaScript. Neither simulation tests physical motors, Bluetooth pairing or radio reliability.

## Build

Open this existing folder in Android Studio. Do not create a second project or copy root-level source files.

Requirements: JDK 17, Android SDK 34 and the included Gradle 8.5 wrapper. Minimum Android version is API 23; target SDK is 34. Set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your SDK, or set `sdk.dir` in an ignored `local.properties`.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --max-workers=2
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`, package `com.robotcontroller.debug`. It is an evaluation build, not a signed store release. No API key, account or backend is needed.

## Use

- **Offline demo:** Start offline demo, then use movement, colour and auto-toggle controls. Replies start with `SIM`. Simulate connection loss tests error handling without hardware. Clear empties the visible data log.
- **Physical robot:** Pair a compatible SPP robot in Android Bluetooth settings first. Refresh, grant Nearby devices permission on Android 12+, select the paired robot, and Connect. Refresh can open the system Bluetooth-enable prompt. App permissions opens this app's Android settings if permission was denied.
- Hold a direction to send movement, then release or cancel to send Stop. Keyboard or accessibility activation sends a movement/Stop pair. The native UI reports the connection, not an unconfirmed firmware auto-mode state.
- Leaving the control screen closes the session. A Stop is queued on departure, but immediate closure, process death or radio loss can prevent delivery. Robot firmware needs its own command timeout/watchdog. The app is not a safety controller.
- The last selected device is remembered; the app does not automatically reconnect.

Only paired devices are listed. The app does not scan for nearby devices or request location. Android 12+ uses `BLUETOOTH_CONNECT`; older releases use manifest Bluetooth permissions. Bluetooth hardware is optional for the offline demo. Standard SPP UUID: `00001101-0000-1000-8000-00805F9B34FB`. The existing reflective channel-1 socket-creation fallback is retained as a best-effort compatibility path; physical behavior is unverified.

## Protocol

| Control | Bytes sent | Interaction |
| --- | --- | --- |
| Forward | `F\n` | Press; `S\n` on release/cancel |
| Backward | `B\n` | Press; `S\n` on release/cancel |
| Left | `L\n` | Press; `S\n` on release/cancel |
| Right | `R\n` | Press; `S\n` on release/cancel |
| Stop | `S\n` | Tap |
| Red / Blue / Green | `RED\n` / `BLUE\n` / `GREEN\n` | Tap |
| Toggle auto mode | `AUTO\n` | Firmware decides the resulting mode |

Incoming UTF-8 lines are shown newest first. Oversized lines are discarded through the next newline to prevent a truncated line being treated as valid data. The visible log is bounded. Socket writes are ordered within a connection and are never redirected to a later session.

## Verification

[Verification notes](docs/verification/README.md), [screen index](docs/verification/SCREEN-INDEX.md) and [test results](docs/verification/RESULTS.md) distinguish native captures, browser captures and hardware limits.

Run the native walkthrough only on a synthetic-data emulator:

```powershell
./scripts/verify-native.ps1 -Serial emulator-5554
```

The script checks the emulator identity, installs this app and its test APK, and clears **only this app's** data. It exercises Android permission/settings screens, so it must not be pointed at a personal device. `-SkipBuild` is appropriate only after checking the exact current source.

Browser checks reuse an installed Playwright package and Chromium:

```powershell
node scripts/check-demo.cjs
node scripts/serve-demo.cjs
```

`PLAYWRIGHT_MODULE` and `CHROMIUM_PATH` can point to existing tools. `DEMO_URL` selects a deployed page; `SCREENSHOT_DIR` chooses capture output. The preview binds only to `127.0.0.1:4349`.

## Source layout

- `app/src/main`: compiled Android UI, resources and transports.
- `app/src/test`: JVM transport/session regression tests using synthetic connections.
- `app/src/androidTest`: real Android UI walkthrough and screenshot collection.
- `docs/demo`: separate browser simulation source.
- `docs/verification`: reproducible evidence and limitations.
- `scripts`: verification, preview and public packaging helpers.

Obsolete root-level copies were removed to leave one compiled source of truth. Gradle wrapper licenses and original Git authorship remain intact.
