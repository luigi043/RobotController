# Screen index

The native app has one scrollable activity. The captures below show its reachable states and directly linked Android system screens on the API 35 emulator. All control inputs and received robot data are synthetic. A native screenshot means a capture of the running Android UI, not a browser reconstruction.

| No. | Screen/state | Reproduction action | Native capture |
| --- | --- | --- | --- |
| 01 | Launch, permission not yet requested | Fresh app data; launch MainActivity | [Open](native/01-launch-no-permission.png) |
| 02 | Nearby devices permission dialog | Refresh | [Open](native/02-nearby-permission-dialog.png) |
| 03 | Permission denied, persistent guidance | Deny the system permission request | [Open](native/03-permission-denied.png) |
| 04 | Android App info, entry to app permissions | App permissions | [Open](native/04-system-app-permissions.png) |
| 05 | Bluetooth-enable confirmation | Allow permission, turn emulator Bluetooth off, Refresh | [Open](native/05-enable-bluetooth-dialog.png) |
| 06 | No paired devices | Allow Bluetooth enable, Refresh | [Open](native/06-no-paired-devices.png) |
| 07 | Android Bluetooth settings | Bluetooth settings | [Open](native/07-system-bluetooth-settings.png) |
| 08 | Native offline demo connected | Return to app, Start offline demo | [Open](native/08-demo-connected.png) |
| 09 | Movement, colour, mode and received-data section | Exercise each control; scroll to Robot Data | [Open](native/09-controls-and-simulated-log.png) |
| 10 | Cleared received-data log | Clear | [Open](native/10-cleared-log.png) |
| 11 | Simulated transport loss and disabled controls | Simulate connection loss | [Open](native/11-simulated-connection-loss.png) |
| 12 | Manual disconnect | Reconnect the demo, Disconnect | [Open](native/12-manual-disconnect.png) |
| 13 | Activity recreated with controls disabled | Connect demo, recreate Activity | [Open](native/13-recreated-disconnected.png) |

The instrumentation assertions also check disabled Connect without a device, movement availability while connected, `F` and `S` responses, the `AUTO` response, Clear, disabled movement after loss, retry and session cleanup. The native demo connection passes through the same session manager and I/O framing as the physical transport.

## Hardware-dependent coverage

| State | Evidence or explicit blocker |
| --- | --- |
| Paired-device dropdown and remembered selection | Requires a paired SPP device; the emulator list was empty. No fabricated hardware entries were inserted. |
| Real connecting / connected robot | No physical robot was available or contacted. The transient connecting state is covered by synthetic JVM transport tests, not a claimed hardware screenshot. |
| Real refused connection or radio loss | JVM refusal/EOF tests and native simulated loss exercise error handling; RF behavior remains unverified. |
| Bluetooth unavailable | Implemented as a persistent message with offline demo available; this emulator exposes a Bluetooth adapter, so that hardware-absence state was not captured. |
| Permanently denied permission on repeated refusal | App info recovery is captured. OS variants and every permanently-denied permission flow were not independently walked through. |
| Real firmware auto state, LED and motor behavior | Protocol commands are tested with synthetic replies. No hardware actuation or firmware-state confirmation is claimed. |

External Settings contains other OS pages. Only the surfaces directly invoked by this app are part of this inventory.

## Browser companion captures

These are real Chromium captures of the separate JavaScript simulation. They are **not native Android screenshots**.

| No. | State | Local capture |
| --- | --- | --- |
| B01 | Disconnected phone controller | [Open](browser/01-disconnected.png) |
| B02 | Connected demo | [Open](browser/02-connected.png) |
| B03 | Holding Forward | [Open](browser/03-hold-forward.png) |
| B04 | Simulated auto mode | [Open](browser/04-auto-mode.png) |
| B05 | Simulated connection loss | [Open](browser/05-connection-loss.png) |
| B06 | Phone-width disconnected page | [Open](browser/06-phone-disconnected.png) |
| B07 | Phone-width connected page | [Open](browser/07-phone-connected.png) |

Deployed-page captures are stored separately under `public/`; the browser filenames map to the same actions. The public native gallery displays the native PNG files above.
