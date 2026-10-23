# 🤖 Robot Controller — Android Bluetooth App

A production-ready Android app in Kotlin that connects to an HC-05 or ESP32 Bluetooth module
and controls a robot in real time using RFCOMM serial commands.

---

## 📁 Project Structure

```
RobotController/
├── app/
│   ├── build.gradle                          ← App-level dependencies
│   └── src/main/
│       ├── AndroidManifest.xml               ← Permissions (Android 10–14+)
│       ├── java/com/robotcontroller/
│       │   ├── MainActivity.kt               ← UI, permissions, button logic
│       │   └── bluetooth/
│       │       ├── BluetoothManager.kt       ← RFCOMM socket + coroutine I/O
│       │       └── CommandHandler.kt         ← Command string constants
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml         ← Full Material Design UI
│           │   ├── spinner_item.xml
│           │   └── spinner_dropdown_item.xml
│           ├── drawable/
│           │   ├── ic_status_connected.xml   ← Green dot
│           │   ├── ic_status_connecting.xml  ← Amber dot
│           │   ├── ic_status_disconnected.xml← Red dot
│           │   ├── bg_spinner.xml            ← Spinner background
│           │   ├── ic_robot.xml              ← Toolbar icon
│           │   └── ic_refresh.xml            ← Refresh icon
│           └── values/
│               ├── colors.xml
│               ├── strings.xml
│               └── themes.xml
└── build.gradle                              ← Project-level plugins
```

---

## 🚀 Quick Setup in Android Studio

### 1. Create a new project
- Open Android Studio → **New Project** → **Empty Views Activity**
- Name: `RobotController`
- Package: `com.robotcontroller`
- Language: Kotlin
- Min SDK: API 23

### 2. Copy files
Replace/create each file listed in the structure above with the provided source files.
The fastest way: use **File > Open** on the `RobotController/` folder directly.

### 3. Sync & Build
- Click **Sync Now** when prompted for Gradle sync
- Run on a real device (Bluetooth does not work on emulators)

---

## 🔑 Permissions Handled

| Android Version | Permissions Used |
|---|---|
| ≤ 11 (API 30) | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` |
| 12+ (API 31+) | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (neverForLocation) |
| All | `VIBRATE` |

Runtime permission requests are handled automatically in `MainActivity`.

---

## 🎮 Command Reference

| Button | Command Sent | Behaviour |
|---|---|---|
| ▲ Forward | `F\n` | Sent on **press**, `S\n` on **release** |
| ▼ Backward | `B\n` | Sent on **press**, `S\n` on **release** |
| ◄ Left | `L\n` | Sent on **press**, `S\n` on **release** |
| ► Right | `R\n` | Sent on **press**, `S\n` on **release** |
| ■ Stop | `S\n` | Tap to stop immediately |
| Red | `RED\n` | Single tap |
| Blue | `BLUE\n` | Single tap |
| Green | `GREEN\n` | Single tap |
| Auto Mode | `AUTO\n` | Toggle (re-sends each tap) |

---

## 📡 Arduino / ESP32 Firmware Example

```cpp
#include <BluetoothSerial.h>
BluetoothSerial BT;

void setup() {
  Serial.begin(115200);
  BT.begin("MyRobot");  // Must match the paired device name
  // setup motor pins, LED pins, etc.
}

void loop() {
  if (BT.available()) {
    String cmd = BT.readStringUntil('\n');
    cmd.trim();

    if      (cmd == "F")    moveForward();
    else if (cmd == "B")    moveBackward();
    else if (cmd == "L")    turnLeft();
    else if (cmd == "R")    turnRight();
    else if (cmd == "S")    stopMotors();
    else if (cmd == "RED")  setLED(255, 0, 0);
    else if (cmd == "BLUE") setLED(0, 0, 255);
    else if (cmd == "GREEN")setLED(0, 255, 0);
    else if (cmd == "AUTO") toggleAutoMode();

    // Optional: send sensor data back
    BT.println("OK:" + cmd);
  }
}
```

For HC-05 use `SoftwareSerial` instead of `BluetoothSerial`:
```cpp
#include <SoftwareSerial.h>
SoftwareSerial bt(10, 11); // RX, TX
// bt.begin(9600);
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────┐
│           MainActivity (UI layer)        │
│  • Observes StateFlow / SharedFlow       │
│  • Handles runtime permissions           │
│  • Vibration feedback on button press    │
│  • Saves/restores last device (Prefs)    │
└────────────────┬────────────────────────┘
                 │ calls
┌────────────────▼────────────────────────┐
│         BluetoothManager                │
│  • Coroutine scope (Dispatchers.IO)      │
│  • RFCOMM socket lifecycle               │
│  • Emits: uiState (StateFlow)            │
│           receivedLine (SharedFlow)      │
│  • Fallback socket for HC-05             │
└────────────────┬────────────────────────┘
                 │ uses
┌────────────────▼────────────────────────┐
│         CommandHandler (object)          │
│  • Command string constants              │
│  • send() helper (checks isConnected)    │
└─────────────────────────────────────────┘
```

---

## 🛠️ Extra Notes

- **Hold-to-move** — directional buttons send continuous command on press, `S\n` on release
- **Auto-reconnect** — last device address is persisted in SharedPreferences; spinner pre-selects it on next launch
- **Thread safety** — all socket I/O runs on `Dispatchers.IO`; UI updates via `lifecycleScope` on Main
- **Graceful cleanup** — `BluetoothManager.cleanup()` is called in `onDestroy()` to close the socket and cancel the coroutine scope, preventing leaks
- **Fallback socket** — if `createRfcommSocketToServiceRecord` fails (common with HC-05), the manager automatically retries with `createRfcommSocket(1)`
