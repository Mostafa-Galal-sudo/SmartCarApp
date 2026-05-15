#include <Servo.h>

// ===== PINS =====
#define SERVO_PIN 3
#define TRIG      12
#define ECHO      4
#define ENR       11
#define IN1       8
#define IN2       9
#define ENL       5
#define IN3       6
#define IN4       7
#define BTN_PIN   2

// ===== SERVO ANGLES =====
#define ANGLE_RIGHT  30
#define ANGLE_FRONT  90
#define ANGLE_LEFT   150

// ===== THRESHOLDS (cm) =====
#define SAFE_DIST      40
#define WARNING_DIST   30
#define CAUTION_DIST   20
#define AVOIDANCE_DIST 15
#define FOLLOW_MAX     20
#define FOLLOW_MIN     7

// ===== SPEEDS =====
#define BASE_SPEED  150
#define SLOW_SPEED  80
#define TURN_SPEED  130
#define RAMP_STEP   10
#define RAMP_DELAY  15

// ===== MODES =====
enum CarMode { MODE_IDLE, MODE_AUTO_MANUAL, MODE_BODY_FOLLOWER, MODE_GYRO };
enum SubMode  { SUB_AUTO, SUB_MANUAL };

CarMode currentMode = MODE_IDLE;
SubMode subMode     = SUB_AUTO;

Servo myServo;

long frontDist = 999;
long leftDist  = 999;
long rightDist = 999;

int currentSpeedR = 0;
int currentSpeedL = 0;

String cmdBuffer = "";

// ==========================================
// 📡 SENSOR LAYER
// ==========================================
long getDistance() {
  digitalWrite(TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG, LOW);
  long dur = pulseIn(ECHO, HIGH, 30000);
  if (dur == 0) return 999;
  return dur * 0.034 / 2;
}

void scanAll() {
  myServo.write(ANGLE_RIGHT); delay(400); rightDist = getDistance();
  myServo.write(ANGLE_FRONT); delay(400); frontDist = getDistance();
  myServo.write(ANGLE_LEFT);  delay(400); leftDist  = getDistance();
  myServo.write(ANGLE_FRONT); delay(200);
}

// ==========================================
// ⚙️ ACTION LAYER
// ==========================================
void move(int right, int left) {
  digitalWrite(IN1, right > 0 ? HIGH : LOW);
  digitalWrite(IN2, right > 0 ? LOW  : HIGH);
  digitalWrite(IN3, left  > 0 ? HIGH : LOW);
  digitalWrite(IN4, left  > 0 ? LOW  : HIGH);
  analogWrite(ENR, abs(right));
  analogWrite(ENL, abs(left));
}

void stopMotors() {
  move(0, 0);
  currentSpeedR = 0;
  currentSpeedL = 0;
}

// ==========================================
// 🚀 SPEED RAMP
// ==========================================
void rampTo(int targetR, int targetL) {
  if (targetR == 0 && targetL == 0) { stopMotors(); return; }

  while (currentSpeedR != targetR || currentSpeedL != targetL) {
    if (currentSpeedR < targetR) currentSpeedR = min(currentSpeedR + RAMP_STEP, targetR);
    else if (currentSpeedR > targetR) currentSpeedR = max(currentSpeedR - RAMP_STEP, targetR);

    if (currentSpeedL < targetL) currentSpeedL = min(currentSpeedL + RAMP_STEP, targetL);
    else if (currentSpeedL > targetL) currentSpeedL = max(currentSpeedL - RAMP_STEP, targetL);

    move(currentSpeedR, currentSpeedL);
    delay(RAMP_DELAY);
  }
}

// ==========================================
// 🎚️ DYNAMIC SPEED
// ==========================================
int dynamicSpeed() {
  if (frontDist >= SAFE_DIST)    return BASE_SPEED;
  if (frontDist <= WARNING_DIST) return SLOW_SPEED;
  return map(frontDist, WARNING_DIST, SAFE_DIST, SLOW_SPEED, BASE_SPEED);
}

// ==========================================
// 🧠 BEST PATH SELECTION
// ==========================================
void avoidanceDecision() {
  scanAll();

  if (leftDist < CAUTION_DIST && rightDist < CAUTION_DIST) {
    rampTo(-BASE_SPEED, -BASE_SPEED);
    delay(800);
    rampTo(0, 0);
    scanAll();
  }

  if (leftDist > rightDist && leftDist > CAUTION_DIST) {
    move(-TURN_SPEED, TURN_SPEED);
    delay(650);
  } else if (rightDist >= leftDist && rightDist > CAUTION_DIST) {
    move(TURN_SPEED, -TURN_SPEED);
    delay(650);
  } else {
    rampTo(-BASE_SPEED, -BASE_SPEED);
    delay(500);
  }

  rampTo(0, 0);
}

// ==========================================
// 🧠 MODE 1 — AUTO / MANUAL
// ==========================================
void decideAndAct() {
  if      (frontDist > SAFE_DIST)      rampTo(dynamicSpeed(), dynamicSpeed());
  else if (frontDist > WARNING_DIST)   rampTo(dynamicSpeed(), dynamicSpeed());
  else if (frontDist > CAUTION_DIST)  { rampTo(0, 0); scanAll(); }
  else if (frontDist > AVOIDANCE_DIST){ rampTo(0, 0); avoidanceDecision(); }
  else {
    rampTo(-BASE_SPEED, -BASE_SPEED);
    delay(600);
    rampTo(0, 0);
    avoidanceDecision();
  }
}

void runAutoManual() {
  if (subMode == SUB_AUTO) {
    frontDist = getDistance();
    decideAndAct();
  }
  // SUB_MANUAL → بيتحكم فيه من BT مباشرة
}

// ==========================================
// 🎯 MODE 2 — BODY FOLLOWER
// ==========================================
void runBodyFollower() {
  // سكّن السيرفو ويسكّن الاتجاهات
  myServo.write(ANGLE_RIGHT); delay(300); rightDist = getDistance();
  myServo.write(ANGLE_FRONT); delay(300); frontDist = getDistance();
  myServo.write(ANGLE_LEFT);  delay(300); leftDist  = getDistance();

  long minDist = min(frontDist, min(leftDist, rightDist));

  // مفيش جسم قريب
  if (minDist > FOLLOW_MAX) {
    myServo.write(ANGLE_FRONT);
    rampTo(0, 0);
    return;
  }

  // قرّب أوي → ارجع
  if (minDist < FOLLOW_MIN) {
    myServo.write(ANGLE_FRONT);
    rampTo(-SLOW_SPEED, -SLOW_SPEED);
    return;
  }

  // تتبع الجسم في اتجاهه
  if (frontDist == minDist) {
    myServo.write(ANGLE_FRONT);
    rampTo(SLOW_SPEED, SLOW_SPEED);
  }
  else if (rightDist == minDist) {
    myServo.write(ANGLE_RIGHT);
    move(TURN_SPEED, -TURN_SPEED);
    delay(300);
    stopMotors();
  }
  else {
    myServo.write(ANGLE_LEFT);
    move(-TURN_SPEED, TURN_SPEED);
    delay(300);
    stopMotors();
  }
}

// ==========================================
// 📱 MODE 3 — GYRO
// الأوامر F/B/L/R/S بتيجي من BT وبتتعالج في processCommand
// ==========================================
void runGyro() {
  // Event-driven من BT — مفيش حاجة في الـ loop
}

// ==========================================
// 📡 BLUETOOTH — Command Parser
// ==========================================
void processCommand(String cmd) {
  // Multi-char commands
  if (cmd == "PAT") { currentMode = MODE_BODY_FOLLOWER; stopMotors(); return; }
  if (cmd == "GYR") { currentMode = MODE_GYRO;          stopMotors(); return; }
  if (cmd.length() != 1) return;

  char c = cmd[0];
  bool isManual = (currentMode == MODE_AUTO_MANUAL && subMode == SUB_MANUAL);
  bool isGyro   = (currentMode == MODE_GYRO);

  switch (c) {
    case 'A':
      currentMode = MODE_AUTO_MANUAL;
      subMode = SUB_AUTO;
      stopMotors();
      break;
    case 'M':
      currentMode = MODE_AUTO_MANUAL;
      subMode = SUB_MANUAL;
      stopMotors();
      break;

    case 'F': if (isManual || isGyro) rampTo(BASE_SPEED,  BASE_SPEED);  break;
    case 'B': if (isManual || isGyro) rampTo(-BASE_SPEED, -BASE_SPEED); break;
    case 'R': if (isManual || isGyro) move(TURN_SPEED, -TURN_SPEED);    break;
    case 'L': if (isManual || isGyro) move(-TURN_SPEED,  TURN_SPEED);   break;
    case 'S': if (isManual || isGyro) rampTo(0, 0);                     break;
  }
}

void handleBluetooth() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      cmdBuffer.trim();
      if (cmdBuffer.length() > 0) {
        processCommand(cmdBuffer);
        cmdBuffer = "";
      }
    } else {
      cmdBuffer += c;
    }
  }
}

// ==========================================
// 🔘 BUTTON — Mode Cycling
// IDLE → MODE1 → MODE2 → MODE3 → IDLE
// ==========================================
#define NUM_MODES 4

void checkButton() {
  static bool lastState = HIGH;
  bool state = digitalRead(BTN_PIN);

  if (lastState == HIGH && state == LOW) {
    currentMode = (CarMode)((currentMode + 1) % NUM_MODES);
    stopMotors();
    myServo.write(ANGLE_FRONT);
    if (currentMode == MODE_AUTO_MANUAL) subMode = SUB_AUTO;
    delay(50);  // debounce
  }
  lastState = state;
}

// ==========================================
// SETUP & LOOP
// ==========================================
void setup() {
  Serial.begin(9600);

  myServo.attach(SERVO_PIN);
  myServo.write(ANGLE_FRONT);
  delay(500);

  pinMode(TRIG, OUTPUT);
  pinMode(ECHO, INPUT);
  pinMode(BTN_PIN, INPUT_PULLUP);
  pinMode(ENR, OUTPUT); pinMode(IN1, OUTPUT); pinMode(IN2, OUTPUT);
  pinMode(ENL, OUTPUT); pinMode(IN3, OUTPUT); pinMode(IN4, OUTPUT);

  stopMotors();  // Startup = IDLE
}

void loop() {
  checkButton();
  handleBluetooth();

  switch (currentMode) {
    case MODE_IDLE:          /* واقفة */        break;
    case MODE_AUTO_MANUAL:   runAutoManual();   break;
    case MODE_BODY_FOLLOWER: runBodyFollower(); break;
    case MODE_GYRO:          runGyro();         break;
  }

  delay(50);
}