# Robot Controller verification

## Scope and baseline

Baseline repository commit: `5a61e2b91317e629af47739b2997bad5f25f90ef`. A previous audit had left uncommitted Bluetooth work and an empty ProGuard file; those changes were preserved and completed. The existing branch was renamed to `robot-controller-verification`. The installed Git identity matches the verified repository author, Luiz Fehlberg.

This is a single-screen Kotlin/Views Android robot controller, not an account-based business application. Its meaningful use is a paired Classic Bluetooth SPP robot or a deliberately labelled offline demonstration. It has no backend, login, subscription, financial records, camera, BLE scanner or cloud storage.

## Toolchain and changes

The original stack is retained: Gradle 8.5, Android Gradle Plugin 8.2.2, Kotlin 1.9.22, JDK 17, compile/target SDK 34 and min SDK 23. The existing shared Android SDK and API 35 emulator were reused. No duplicate SDK, emulator or browser installation was introduced. No connected physical phone or robot was used.

| Problem found | Change |
| --- | --- |
| Disconnect waited for a blocking read before closing the socket | Detach the session and close the transport to unblock I/O |
| Independent send coroutines could reorder movement and Stop | One ordered writer per session |
| Queued writes could target mutable global output streams after reconnection | Queue and transport belong to the same immutable session |
| Remote EOF could spin without leaving Connected | EOF becomes a recoverable connection error and closes resources |
| Connection cancellation and old errors could replace a new session | Session-identity guards around state changes |
| UTF-8 could be split across byte packets | Streaming reader, bounded newline framing and oversized-line discard |
| Activity read protected adapter state before permission | Permission-gated device loading and socket creation |
| Denial/off/no-device states were temporary toasts | Persistent guidance, system enable prompt and settings actions |
| Permission denial guidance was overwritten on resume | Preserve the denied state through device refresh |
| Empty selector still allowed Connect | Disable unavailable actions and controls |
| Local auto-mode boolean claimed an unconfirmed firmware state | Send an explicit toggle without claiming hardware ON/OFF |
| Controls required a paired robot even for evaluation | Separate in-memory native demo and simulated link-loss action |
| Movement arrows lacked accessible names and click behavior | English content descriptions and bounded accessibility activation |
| Connection label wrapped in a short button | Normal-case labels with appropriate padding and text size |
| Fixed light surfaces inherited a dark theme | Consistent light Material theme and compatible system navigation icons |
| Root copies and Java daemon metadata conflicted with actual project setup | One source tree and JDK 17 setup; wrapper remains unchanged |
| Release configuration named a missing ProGuard rules file | Add the referenced app-specific rules file |

Haptic feedback and the legacy channel-1 socket-creation fallback remain. No live Bluetooth integration success is claimed. Debug validation does not establish release/minification compatibility or store readiness.

## Screen and dependency inventory

`MainActivity` is the only app activity. Its scrollable screen contains Connection, Movement Control, Colour & Mode and Robot Data. The offline demo uses that same native screen with an in-memory transport. The browser page is independent JavaScript, not an Android emulator.

External Android surfaces are Nearby devices permission, Bluetooth enable confirmation, app details/permissions settings and Bluetooth settings. Their language and layout belong to the OS. The app itself forces English resources.

A real hardware session needs an enabled Bluetooth adapter, runtime permission on API 31+, a paired Classic Bluetooth device exposing SPP, and compatible firmware. No location or scanning permission is required. BLE-only peripherals are outside this protocol. Robot motor response, radio loss and firmware state cannot be established from simulated replies.

## Reproduce

Follow the build and verification commands in the root README. Native captures are produced by Android instrumentation through `UiAutomation.takeScreenshot`, not drawn or reconstructed. The emulator-only guard is checked both by the PowerShell script and the native test. Screens and actions are mapped in [SCREEN-INDEX.md](SCREEN-INDEX.md). Final checks and public artifact details are recorded in [RESULTS.md](RESULTS.md).

## Limits

- Native runtime coverage is the existing API 35 emulator. API 23 through 34 and different screen/font settings have not all been walked through.
- Paired-device selection, real connect progress, successful RFCOMM connection, firmware responses, fallback compatibility and physical motor/LED behavior require hardware. No commands were sent to real hardware.
- Leaving the screen disconnects. Stop delivery cannot be guaranteed on departure, write failure, process termination or radio loss; firmware must enforce its own timeout. Bluetooth writes have no application-level acknowledgement/retry protocol.
- Auto mode is a firmware toggle; the native UI does not know its resulting state. Browser auto movement is an illustrative local animation.
- Release signing, store requirements, release minification and a hardware safety evaluation remain outside this debug audit.
- The APK is publicly downloadable for evaluation. It needs sideloading and is not distributed through an app store.
