package com.example.smartcar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // ===== UI =====
    private TextView tvModeLabel, tvStatusIndicator;
    private TextView lblBodyStatus, lblGyroStatus;
    private TextView lblVoiceStatus, lblRecognizedText, lblConfidence, lblConfirmQuestion;
    private View layoutManual, layoutBody, layoutGyro, layoutVoice;
    private LinearLayout layoutConfirm;
    private Button btnOpenBluetooth, btnNavManual, btnNavBody, btnNavGyro, btnNavVoice;
    private Button btnFwd, btnBack, btnLeft, btnRight, btnStop, btnAuto, btnMan;
    private Button btnBodyActivate, btnMic, btnConfirmYes, btnConfirmNo;
    private SeekBar sliderSensitivity;

    // ===== BLUETOOTH =====
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean isConnected = false;
    private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Dialog bluetoothDialog;

    // ===== COMMAND =====
    private String lastCmd = "";
    private boolean cmdDelayActive = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // ===== GYRO =====
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isGyroActive = false;
    private float threshold = 15f;
    private float r1x, r2x, r3x, r1y, r2y, r3y;

    // ===== VOICE =====
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private String pendingIntent = "";

    // ===== DICTIONARIES =====
    private static final List<String> DICT_F = Arrays.asList(
        "forward", "go", "ahead", "move", "drive", "straight", "fwd",
        "emshi", "قدام", "تحرك", "امشي", "يلا", "روح", "هيا"
    );
    private static final List<String> DICT_B = Arrays.asList(
        "back", "backward", "reverse", "return",
        "ارجع", "ورا", "خلف", "رجوع", "تراجع"
    );
    private static final List<String> DICT_R = Arrays.asList(
        "right", "turn right",
        "يمين", "لف يمين", "الى اليمين"
    );
    private static final List<String> DICT_L = Arrays.asList(
        "left", "turn left",
        "شمال", "يسار", "لف شمال", "لف يسار"
    );
    private static final List<String> DICT_S = Arrays.asList(
        "stop", "halt", "freeze", "wait", "pause",
        "قف", "اوقف", "بطل", "وقف", "استنى", "انتظر"
    );

    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int VOICE_PERMISSION_CODE   = 102;

    // ==========================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        setupListeners();
        setupSensors();
        checkPermissions();
        initVoiceEngine();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Connection safety checker
        handler.post(new Runnable() {
            @Override public void run() {
                if (isConnected && bluetoothSocket != null && !bluetoothSocket.isConnected())
                    handleDisconnect();
                handler.postDelayed(this, 1000);
            }
        });
    }

    // ==========================================
    // INIT UI
    // ==========================================
    private void initUI() {
        tvModeLabel       = findViewById(R.id.tvModeLabel);
        tvStatusIndicator = findViewById(R.id.tvStatusIndicator);
        lblBodyStatus     = findViewById(R.id.lblBodyStatus);
        lblGyroStatus     = findViewById(R.id.lblGyroStatus);
        lblVoiceStatus    = findViewById(R.id.lblVoiceStatus);
        lblRecognizedText = findViewById(R.id.lblRecognizedText);
        lblConfidence     = findViewById(R.id.lblConfidence);
        lblConfirmQuestion= findViewById(R.id.lblConfirmQuestion);
        layoutConfirm     = findViewById(R.id.layoutConfirm);

        layoutManual = findViewById(R.id.layoutManual);
        layoutBody   = findViewById(R.id.layoutBody);
        layoutGyro   = findViewById(R.id.layoutGyro);
        layoutVoice  = findViewById(R.id.layoutVoice);

        btnOpenBluetooth = findViewById(R.id.btnOpenBluetooth);
        btnNavManual     = findViewById(R.id.btnNavManual);
        btnNavBody       = findViewById(R.id.btnNavBody);
        btnNavGyro       = findViewById(R.id.btnNavGyro);
        btnNavVoice      = findViewById(R.id.btnNavVoice);

        btnFwd          = findViewById(R.id.btnFwd);
        btnBack         = findViewById(R.id.btnBack);
        btnLeft         = findViewById(R.id.btnLeft);
        btnRight        = findViewById(R.id.btnRight);
        btnStop         = findViewById(R.id.btnStop);
        btnAuto         = findViewById(R.id.btnAuto);
        btnMan          = findViewById(R.id.btnMan);
        btnBodyActivate = findViewById(R.id.btnBodyActivate);
        btnMic          = findViewById(R.id.btnMic);
        btnConfirmYes   = findViewById(R.id.btnConfirmYes);
        btnConfirmNo    = findViewById(R.id.btnConfirmNo);

        sliderSensitivity = findViewById(R.id.sliderSensitivity);
    }

    // ==========================================
    // LISTENERS
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        btnOpenBluetooth.setOnClickListener(v -> showBluetoothDialog());

        btnNavManual.setOnClickListener(v -> switchMode("Manual"));
        btnNavBody.setOnClickListener(v   -> switchMode("Body"));
        btnNavGyro.setOnClickListener(v   -> switchMode("Gyro"));
        btnNavVoice.setOnClickListener(v  -> switchMode("Voice"));

        // D-PAD
        View.OnTouchListener dpad = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int id = v.getId();
                if      (id == R.id.btnFwd)   sendCommand("F");
                else if (id == R.id.btnBack)  sendCommand("B");
                else if (id == R.id.btnLeft)  sendCommand("L");
                else if (id == R.id.btnRight) sendCommand("R");
            } else if (event.getAction() == MotionEvent.ACTION_UP ||
                       event.getAction() == MotionEvent.ACTION_CANCEL) {
                sendCommand("S");
            }
            return false;
        };
        btnFwd.setOnTouchListener(dpad);
        btnBack.setOnTouchListener(dpad);
        btnLeft.setOnTouchListener(dpad);
        btnRight.setOnTouchListener(dpad);

        btnStop.setOnClickListener(v -> sendCommand("S"));
        btnAuto.setOnClickListener(v -> sendCommand("A"));
        btnMan.setOnClickListener(v  -> sendCommand("M"));

        // Body
        btnBodyActivate.setOnClickListener(v -> {
            sendCommand("PAT");
            lblBodyStatus.setVisibility(View.VISIBLE);
        });

        // Gyro slider
        sliderSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { threshold = p; }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        // Voice mic button
        btnMic.setOnClickListener(v -> {
            if (isListening) stopListening();
            else             startListening();
        });

        // Confirmation buttons
        btnConfirmYes.setOnClickListener(v -> {
            if (!pendingIntent.isEmpty()) {
                sendCommand(pendingIntent);
                showVoiceResult("// COMMAND SENT: " + intentLabel(pendingIntent), true);
            }
            layoutConfirm.setVisibility(View.GONE);
            pendingIntent = "";
        });

        btnConfirmNo.setOnClickListener(v -> {
            layoutConfirm.setVisibility(View.GONE);
            pendingIntent = "";
            lblVoiceStatus.setText("PRESS TO SPEAK");
            lblRecognizedText.setText("");
            lblConfidence.setText("");
        });
    }

    // ==========================================
    // SENSORS
    // ==========================================
    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null)
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    // ==========================================
    // PERMISSIONS
    // ==========================================
    private void checkPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.RECORD_AUDIO);

        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
    }

    // ==========================================
    // VOICE ENGINE
    // ==========================================
    private void initVoiceEngine() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p)  {
                lblVoiceStatus.setText("// LISTENING...");
                btnMic.setBackgroundTintList(
                    getResources().getColorStateList(R.color.status_green, null));
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {
                isListening = false;
                btnMic.setBackgroundTintList(
                    getResources().getColorStateList(R.color.button_dark, null));
                lblVoiceStatus.setText("// PROCESSING...");
            }
            @Override public void onError(int error) {
                isListening = false;
                btnMic.setBackgroundTintList(
                    getResources().getColorStateList(R.color.button_dark, null));
                lblVoiceStatus.setText("// ERROR — TRY AGAIN");
                lblRecognizedText.setText("");
                lblConfidence.setText("");
            }
            @Override public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty())
                    handleVoiceResult(matches.get(0));
            }
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int t, Bundle b) {}
        });
    }

    private void startListening() {
        if (speechRecognizer == null) return;
        isListening = true;
        layoutConfirm.setVisibility(View.GONE);
        pendingIntent = "";

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        isListening = false;
        btnMic.setBackgroundTintList(
            getResources().getColorStateList(R.color.button_dark, null));
        lblVoiceStatus.setText("PRESS TO SPEAK");
    }

    // ==========================================
    // VOICE PROCESSING
    // ==========================================
    private void handleVoiceResult(String raw) {
        String text = normalizeText(raw);
        lblRecognizedText.setText("\"" + raw + "\"");

        Map<String, Double> scores = new HashMap<>();
        scores.put("F", matchScore(text, DICT_F));
        scores.put("B", matchScore(text, DICT_B));
        scores.put("R", matchScore(text, DICT_R));
        scores.put("L", matchScore(text, DICT_L));
        scores.put("S", matchScore(text, DICT_S));

        String best = "S";
        double bestScore = 0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }

        int pct = (int)(bestScore * 100);
        lblConfidence.setText("Confidence: " + pct + "%");

        if (bestScore >= 0.7) {
            // ACCEPT
            sendCommand(best);
            showVoiceResult("// COMMAND: " + intentLabel(best), true);

        } else if (bestScore >= 0.4) {
            // CONFIRM
            pendingIntent = best;
            lblConfirmQuestion.setText("Did you mean: " + intentLabel(best) + " ?");
            layoutConfirm.setVisibility(View.VISIBLE);
            lblVoiceStatus.setText("// CONFIRM COMMAND");

        } else {
            // REJECT
            showVoiceResult("// NOT RECOGNIZED", false);
        }
    }

    private void showVoiceResult(String status, boolean accepted) {
        lblVoiceStatus.setText(status);
        lblVoiceStatus.setTextColor(accepted
            ? getColor(R.color.status_green)
            : getColor(R.color.status_red));

        handler.postDelayed(() -> {
            lblVoiceStatus.setText("PRESS TO SPEAK");
            lblVoiceStatus.setTextColor(getColor(R.color.text_dim));
        }, 2000);
    }

    private String intentLabel(String cmd) {
        switch (cmd) {
            case "F": return "FORWARD";
            case "B": return "BACKWARD";
            case "R": return "RIGHT";
            case "L": return "LEFT";
            case "S": return "STOP";
            default:  return cmd;
        }
    }

    // ==========================================
    // NLP ENGINE
    // ==========================================
    private String normalizeText(String input) {
        return input.toLowerCase()
            .replaceAll("[^a-zA-Z\u0600-\u06FF ]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private double matchScore(String input, List<String> dict) {
        double best = 0;
        for (String word : dict) {
            if (input.equals(word))          return 1.0;
            if (input.contains(word))        best = Math.max(best, 0.7);
            if (levenshtein(input, word) <= 2) best = Math.max(best, 0.6);
        }
        return best;
    }

    private int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++)
                dp[i][j] = a.charAt(i-1) == b.charAt(j-1)
                    ? dp[i-1][j-1]
                    : 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
        return dp[m][n];
    }

    // ==========================================
    // BLUETOOTH
    // ==========================================
    @SuppressLint("MissingPermission")
    private void showBluetoothDialog() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        bluetoothDialog = new Dialog(this);
        bluetoothDialog.setContentView(R.layout.dialog_bluetooth_devices);

        ListView lvDevices    = bluetoothDialog.findViewById(R.id.lvDevices);
        Button btnDisconnect  = bluetoothDialog.findViewById(R.id.btnDisconnect);

        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
        List<String> nameList = new ArrayList<>();
        List<BluetoothDevice> devList = new ArrayList<>();
        for (BluetoothDevice d : paired) {
            nameList.add(d.getName() + "\n" + d.getAddress());
            devList.add(d);
        }

        lvDevices.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1, nameList));
        lvDevices.setOnItemClickListener((p, v, pos, id) -> {
            connectToDevice(devList.get(pos));
            bluetoothDialog.dismiss();
        });
        btnDisconnect.setOnClickListener(v -> {
            handleDisconnect();
            bluetoothDialog.dismiss();
        });
        bluetoothDialog.show();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                isConnected  = true;
                runOnUiThread(() -> {
                    tvStatusIndicator.setTextColor(Color.GREEN);
                    Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                isConnected = false;
                runOnUiThread(() ->
                    Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show());
                try { if (bluetoothSocket != null) bluetoothSocket.close(); }
                catch (IOException ignored) {}
            }
        }).start();
    }

    private void handleDisconnect() {
        isConnected = false;
        sendCommand("S");
        try { if (bluetoothSocket != null) bluetoothSocket.close(); }
        catch (IOException ignored) {}
        runOnUiThread(() -> tvStatusIndicator.setTextColor(Color.RED));
    }

    private void sendCommand(String cmd) {
        if (!isConnected || outputStream == null) return;
        if (cmdDelayActive) return;
        try {
            outputStream.write((cmd + "\n").getBytes());
            lastCmd = cmd;
            cmdDelayActive = true;
            handler.postDelayed(() -> cmdDelayActive = false, 80);
        } catch (IOException e) {
            handleDisconnect();
        }
    }

    // ==========================================
    // MODE SWITCHING
    // ==========================================
    private void switchMode(String mode) {
        sendCommand("S");
        layoutManual.setVisibility(View.GONE);
        layoutBody.setVisibility(View.GONE);
        layoutGyro.setVisibility(View.GONE);
        layoutVoice.setVisibility(View.GONE);
        isGyroActive = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (isListening) stopListening();

        switch (mode) {
            case "Manual":
                layoutManual.setVisibility(View.VISIBLE);
                tvModeLabel.setText("// MODE: MANUAL");
                break;
            case "Body":
                layoutBody.setVisibility(View.VISIBLE);
                lblBodyStatus.setVisibility(View.INVISIBLE);
                tvModeLabel.setText("// MODE: BODY FOLLOWER");
                sendCommand("PAT");
                break;
            case "Gyro":
                sendCommand("GYR");
                layoutGyro.setVisibility(View.VISIBLE);
                tvModeLabel.setText("// MODE: GYRO");
                isGyroActive = true;
                if (sensorManager != null && accelerometer != null)
                    sensorManager.registerListener(this, accelerometer,
                        SensorManager.SENSOR_DELAY_UI);
                break;
            case "Voice":
                sendCommand("M");
                layoutVoice.setVisibility(View.VISIBLE);
                tvModeLabel.setText("// MODE: VOICE AI");
                layoutConfirm.setVisibility(View.GONE);
                lblVoiceStatus.setText("PRESS TO SPEAK");
                lblRecognizedText.setText("");
                lblConfidence.setText("");
                break;
        }
    }

    // ==========================================
    // GYRO
    // ==========================================
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isGyroActive) return;

        r1x = r2x; r2x = r3x; r3x = event.values[0];
        r1y = r2y; r2y = r3y; r3y = event.values[1];

        float avgX = (r1x + r2x + r3x) / 3f;
        float avgY = (r1y + r2y + r3y) / 3f;
        float thr  = threshold / 5.0f;

        String target, status;
        if      (avgY >  thr) { target = "F"; status = "Forward";   }
        else if (avgY < -thr) { target = "B"; status = "Backward";  }
        else if (avgX >  thr) { target = "R"; status = "Right";     }
        else if (avgX < -thr) { target = "L"; status = "Left";      }
        else                  { target = "S"; status = "Stop / Flat";}

        lblGyroStatus.setText(status);
        if (!target.equals(lastCmd)) sendCommand(target);
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ==========================================
    // LIFECYCLE
    // ==========================================
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handleDisconnect();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
        }
    }
}
