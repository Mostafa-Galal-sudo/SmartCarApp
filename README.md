# 🚗 SmartCar — Intelligent Robot Car System

A multi-layered intelligent robot car system that integrates an **Arduino-based vehicle**, an **Android control application**, and a **real-time web dashboard** — all communicating seamlessly over Bluetooth and WiFi.

---

## 📐 System Architecture

```
┌─────────────────────────────────────────────────────┐
│               Web Dashboard (Browser)                │
│         HTML + CSS + JavaScript (app.js)             │
│   D-Pad · Radar · Gauges · Draw Path · System Log   │
└──────────────────────┬──────────────────────────────┘
                       │ WebSocket  ws://PHONE_IP:8080
┌──────────────────────▼──────────────────────────────┐
│            Android App — SmartCarApp                 │
│                  Java · Android SDK                  │
│  Biometric Auth · Voice AI · Gyro · Camera · Audio  │
│         WebSocket Server (Bridge) on :8080           │
└──────────────────────┬──────────────────────────────┘
                       │ Bluetooth Classic (SPP)
┌──────────────────────▼──────────────────────────────┐
│              Arduino Uno — SmartCar.ino              │
│   Ultrasonic Sensors · Motors · Servo · HC-05 BT    │
│     Auto Navigation · Body Follower · Safety Layer  │
└─────────────────────────────────────────────────────┘
```

---

## 📁 Project Structure

```
SmartCar/
│
├── SmartCar.ino                          # Arduino firmware
│
├── SmartCarApp/                          # Android Application
│   ├── app/
│   │   ├── build.gradle                  # Dependencies (incl. Java-WebSocket)
│   │   └── src/main/
│   │       ├── AndroidManifest.xml       # Permissions & app config
│   │       └── java/com/example/smartcar/
│   │           └── MainActivity.java     # All app logic
│
└── Dashboard/                            # Web Dashboard
    ├── index.html                        # UI layout & structure
    ├── style.css                         # Styling & animations
    └── app.js                            # WebSocket & control logic
```

---

## ⚙️ Hardware Components

| Component | Role |
|-----------|------|
| Arduino Uno | Main controller |
| HC-05 Bluetooth | Serial communication with phone |
| Ultrasonic Sensor (x1 + Servo) | Distance scanning — Left / Front / Right |
| L298N Motor Driver | Controls 2 DC motors |
| DC Motors (x2) | Left and right wheel drive |
| Servo Motor | Rotates ultrasonic for scanning |
| Push Button | Physical mode cycling |
| LED (Pin 13) | Mode indicator via blink count |

### Pin Mapping

| Pin | Component |
|-----|-----------|
| 3 | Servo |
| 12 | Ultrasonic TRIG |
| 4 | Ultrasonic ECHO |
| 11 | ENR (Right Motor Speed) |
| 8, 9 | IN1, IN2 (Right Motor Direction) |
| 5 | ENL (Left Motor Speed) |
| 6, 7 | IN3, IN4 (Left Motor Direction) |
| 2 | Push Button |
| 13 | LED Indicator |
| 0, 1 | HC-05 Bluetooth (TX/RX) |

---

## 🎮 Control Modes

### 1. Manual Mode
User controls the car directly via on-screen D-Pad buttons or keyboard arrows on the dashboard. Commands are sent in real-time; releasing a button immediately stops the car.

**Code location:** `MainActivity.java` → `setupListeners()` → `dpad` touch listener

---

### 2. Autonomous Mode
The car navigates independently using ultrasonic sensor readings. It applies dynamic speed based on obstacle distance and uses a best-path selection algorithm to avoid collisions.

**Code location:**
- `SmartCar.ino` → `runAutoManual()` → `decideAndAct()` → `avoidanceDecision()`

---

### 3. Body Follower Mode
The servo sweeps Left/Front/Right to scan for the nearest object. Once locked, the car follows it within a defined range (10–20 cm). If the object moves out of range, it re-enters scanning mode.

**Code location:**
- `SmartCar.ino` → `runBodyFollower()`
- States: `SCANNING` → `LOCKED`

---

### 4. Gyroscope Mode
The phone's accelerometer is read continuously. Tilting the phone forward/backward/left/right sends the corresponding movement command to the car. A 3-sample rolling average smooths out jitter.

**Code location:**
- `MainActivity.java` → `onSensorChanged()` → `SensorManager`

---

### 5. Voice Control Mode
Supports Arabic and English commands using Android's built-in Speech Recognition engine. A confidence scoring system decides whether to execute immediately, ask for confirmation, or reject. The Levenshtein distance algorithm handles pronunciation variations.

**Commands:**
| Action | English | Arabic |
|--------|---------|--------|
| Forward | go, drive, ahead | قدام، امشي، يلا |
| Backward | back, reverse | ارجع، ورا، تراجع |
| Right | right, turn right | يمين، لف يمين |
| Left | left, turn left | شمال، يسار |
| Stop | stop, halt, freeze | قف، اوقف، وقف |

**Code location:**
- `MainActivity.java` → `handleVoiceResult()` · `matchScore()` · `levenshtein()`
- Dictionaries: `DICT_F` · `DICT_B` · `DICT_R` · `DICT_L` · `DICT_S`

---

### 6. Line Follower Mode (Camera)
Uses the phone camera instead of traditional IR sensors. Each frame is analyzed in grayscale — the dark line is detected by pixel brightness threshold. Based on the line's position relative to the image center, Left/Forward/Right commands are issued.

**Code location:**
- `MainActivity.java` → `LineAnalyzer` class → `analyze()`
- Camera: `CameraX API` → `ImageAnalysis`

---

### 7. Clap Control Mode
The microphone continuously monitors audio amplitude. A sudden spike above threshold counts as a clap. Claps are grouped within a 1.2-second window:
- 1 clap → Stop
- 2 claps → Forward
- 3 claps → Backward

**Code location:**
- `MainActivity.java` → `ClapRunnable` class → `executeClapCommand()`

---

### 8. Music Rhythm Mode
Analyzes ambient audio in real-time using spectral flux — detecting sudden energy spikes that indicate beats. The car moves in sync with the music rhythm.

**Code location:**
- `MainActivity.java` → `MusicRunnable` class

---

### 9. Draw Path Mode
The user draws a path with their finger on the phone screen. The app analyzes the drawn points, converts direction changes into timed commands (F/L/R), and executes them sequentially on the car.

**Code location:**
- `MainActivity.java` → `playDrawPath()` · `DrawPathView` class

---

## 🔐 Biometric Security

Before any Bluetooth connection is established, the user must authenticate using the device's biometric system (fingerprint or face recognition) via Android's `BiometricPrompt API`.

**How it works:**
1. User selects a Bluetooth device from the paired list
2. Biometric prompt appears immediately
3. On success → `connectToDevice()` is called
4. On failure or cancel → connection is blocked entirely

**Why biometric?**
- Fingerprints cannot be guessed or replicated
- Authentication happens at hardware level (Secure Enclave) — credentials never touch the app
- Even if the phone is stolen, the car cannot be controlled

**Code location:**
- `MainActivity.java` → `showBiometricAuth()` → `BiometricPrompt`
- On success: `onAuthenticationSucceeded()` → `connectToDevice()`
- On failure: `onAuthenticationError()` → `pendingAuthDevice = null`

---

## 📡 Real-Time Telemetry

The Arduino sends sensor data every 200ms via Bluetooth Serial in this format:

```
F:120,L:45,R:30,ML:150,MR:150,ST:SCANNING,ANG:90,MOD:AUTO_MANUAL,SUB:AUTO
```

| Field | Meaning |
|-------|---------|
| F | Front distance (cm) |
| L | Left distance (cm) |
| R | Right distance (cm) |
| ML | Left motor speed (0–255) |
| MR | Right motor speed (0–255) |
| ST | Follower state (SCANNING / LOCKED) |
| ANG | Servo locked angle |
| MOD | Current mode |
| SUB | Sub-mode (AUTO / MANUAL) |

The Android app reads this via `parseLine()`, converts it to JSON, and broadcasts it to all connected dashboard clients via WebSocket:

```json
{
  "t": "telemetry",
  "mode": "AUTO_MANUAL",
  "dist": { "f": 120, "l": 45, "r": 30 },
  "speeds": { "l": 150, "r": 150 },
  "followerState": "SCANNING",
  "lockedAngle": 90
}
```

**Code location:**
- Arduino: `SmartCar.ino` → `sendTelemetry()`
- Android: `MainActivity.java` → `startArduinoReader()` · `parseLine()` · `buildTelemetryJson()`
- Dashboard: `app.js` → `ws.onmessage` → `updateGauges()` · `updateMotorBars()`

---

## 🌐 WebSocket Bridge

The Android app runs an embedded WebSocket Server on port **8080** using the `Java-WebSocket` library. This allows the web dashboard (running on any laptop or PC on the same WiFi network) to connect directly to the phone.

```
Dashboard (Browser)  ←→  ws://PHONE_IP:8080  ←→  Android App  ←→  Arduino
```

- Dashboard → Android: control commands (F, B, L, R, S, A, M ...)
- Android → Dashboard: telemetry JSON every 200ms

**Code location:**
- `MainActivity.java` → `SmartCarWSServer` class (inner class)
- Server start: `startWebSocketServer()` in `onCreate()`
- Server stop: `stopWebSocketServer()` in `onDestroy()`

---

## 🛡️ Safety Layer

Every phone-controlled mode (Gyro, Line, Clap, Music, Draw) includes an independent safety check running every 300–500ms. If the front ultrasonic sensor detects an obstacle closer than 20 cm, the motors stop immediately — regardless of what command the phone just sent.

**Code location:**
- `SmartCar.ino` → `runGyro()` · `runLineFollower()` · `runClap()` · `runMusic()` · `runDrawPath()`

---

## 🖥️ Web Dashboard Features

| Feature | Description |
|---------|-------------|
| Distance Gauges | Live LEFT / FRONT / RIGHT in cm |
| Motor Speed Bars | Real-time left and right motor PWM |
| Radar Canvas | Animated sweep showing obstacle detection |
| D-Pad Control | Click or keyboard arrows for manual control |
| Mode Panels | Body Follower · Gyro · Audio · Draw Path |
| System Log | Color-coded TX / RX / ERR messages with timestamps |
| Phone Bridge Indicator | Green when WebSocket is connected |
| Arduino Link Indicator | Green when telemetry data is flowing |

---

## 🚀 Setup & Running

### Arduino
1. Open `SmartCar.ino` in Arduino IDE
2. Select **Arduino Uno** and correct COM port
3. Upload — verify Serial Monitor shows telemetry at 9600 baud (disconnect HC-05 TX/RX first to test)

### Android App
1. Open project in **Android Studio**
2. Build APK: `Build → Build Bundle(s)/APK(s) → Build APK(s)`
3. Install on phone
4. Grant all permissions (Bluetooth, Microphone, Camera)
5. Enroll fingerprint in phone Settings if not already done

### Dashboard
1. Open `Dashboard/index.html` directly in any browser (no server needed)
2. Find phone IP: Settings → WiFi → tap network → IP Address
3. Enter `ws://PHONE_IP:8080` in the connection field
4. Click **CONNECT**

### Connection Order
```
1. Phone & laptop on same WiFi
2. Open app on phone
3. Connect app to Arduino via Bluetooth (biometric auth)
4. Open dashboard in browser
5. Connect dashboard to phone WebSocket
```

---

## 📦 Dependencies

### Android (`app/build.gradle`)
```gradle
implementation 'androidx.biometric:biometric:1.1.0'
implementation 'org.java-websocket:Java-WebSocket:1.5.3'
implementation 'androidx.camera:camera-core:1.3.0'
implementation 'androidx.camera:camera-view:1.3.0'
```

### Arduino (built-in libraries)
- `Servo.h` — standard Arduino library

### Dashboard
- Pure HTML / CSS / JavaScript — no frameworks, no installation required

---

## 👨‍💻 Team & Responsibilities

| Module | Technology |
|--------|------------|
| Arduino Firmware | C++ (Arduino) |
| Android App | Java · Android SDK |
| Biometric Security | BiometricPrompt API |
| WebSocket Bridge | Java-WebSocket Library |
| Web Dashboard | HTML · CSS · Vanilla JS |
