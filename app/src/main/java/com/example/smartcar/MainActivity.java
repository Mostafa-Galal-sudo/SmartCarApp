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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "SmartCarApp";

    // ===== UI =====
    private TextView tvModeLabel, tvStatusIndicator;
    private TextView lblBodyStatus, lblGyroStatus;
    private TextView lblVoiceStatus, lblRecognizedText, lblConfidence, lblConfirmQuestion;
    private View layoutManual, layoutBody, layoutGyro, layoutVoice;
    private LinearLayout layoutConfirm;
    private Button btnOpenBluetooth, btnNavManual, btnNavBody, btnNavGyro, btnNavVoice;
    private Button btnNavLine, btnNavClap, btnNavMusic, btnNavDraw;
    private Button btnFwd, btnBack, btnLeft, btnRight, btnStop, btnAuto, btnMan;
    private Button btnBodyActivate, btnMic, btnConfirmYes, btnConfirmNo;
    private SeekBar sliderSensitivity;

    // ===== NEW MODE UI =====
    private View layoutLine, layoutClap, layoutMusic, layoutDraw;
    private PreviewView previewLine;
    private TextView tvLineStatus, tvClapStatus, tvClapCount, tvMusicStatus, tvDrawStatus;
    private SeekBar sliderLineThreshold;
    private Button btnLineStart, btnClapMic, btnMusicStart, btnDrawPlay, btnDrawClear;
    private DrawPathView drawPathView;
    private View visualizerView;

    // ===== BLUETOOTH =====
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean isConnected = false;
    private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Dialog bluetoothDialog;

    // ===== BIOMETRIC SECURITY =====
    private BluetoothDevice pendingAuthDevice = null;

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

    // ===== LINE FOLLOWER (Camera) =====
    private ExecutorService cameraExecutor;
    private boolean isLineTracking = false;
    private int lineThreshold = 80;
    private ProcessCameraProvider cameraProvider;

    // ===== CLAP CONTROL =====
    private boolean isClapActive = false;
    private AudioRecord clapAudioRecord;
    private Thread clapThread;
    private long lastClapTime = 0;
    private int clapCount = 0;
    private static final double CLAP_THRESHOLD = 5000.0;
    private final AtomicLong clapWindowStart = new AtomicLong(0);
    private final AtomicInteger windowClaps = new AtomicInteger(0);

    // ===== MUSIC RHYTHM =====
    private boolean isMusicActive = false;
    private AudioRecord musicAudioRecord;
    private Thread musicThread;
    private float[] visualizerBars = new float[20];

    // ===== DRAW PATH =====
    private boolean isDrawingPlaying = false;

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

    // ==========================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            initUI();
            setupListeners();
            setupSensors();
            checkPermissions();
            initVoiceEngine();

            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            cameraExecutor = Executors.newSingleThreadExecutor();

            // Connection safety checker
            handler.post(new Runnable() {
                @Override public void run() {
                    if (isConnected && bluetoothSocket != null && !bluetoothSocket.isConnected())
                        handleDisconnect();
                    handler.postDelayed(this, 1000);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in onCreate", e);
            Toast.makeText(this, "App Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==========================================
    // INIT UI
    // ==========================================
    private void initUI() {
        try {
            tvModeLabel = findViewById(R.id.tvModeLabel);
            tvStatusIndicator = findViewById(R.id.tvStatusIndicator);
            lblBodyStatus = findViewById(R.id.lblBodyStatus);
            lblGyroStatus = findViewById(R.id.lblGyroStatus);
            lblVoiceStatus = findViewById(R.id.lblVoiceStatus);
            lblRecognizedText = findViewById(R.id.lblRecognizedText);
            lblConfidence = findViewById(R.id.lblConfidence);
            lblConfirmQuestion= findViewById(R.id.lblConfirmQuestion);
            layoutConfirm = findViewById(R.id.layoutConfirm);

            layoutManual = findViewById(R.id.layoutManual);
            layoutBody = findViewById(R.id.layoutBody);
            layoutGyro = findViewById(R.id.layoutGyro);
            layoutVoice = findViewById(R.id.layoutVoice);

            btnOpenBluetooth = findViewById(R.id.btnOpenBluetooth);
            btnNavManual = findViewById(R.id.btnNavManual);
            btnNavBody = findViewById(R.id.btnNavBody);
            btnNavGyro = findViewById(R.id.btnNavGyro);
            btnNavVoice = findViewById(R.id.btnNavVoice);

            btnFwd = findViewById(R.id.btnFwd);
            btnBack = findViewById(R.id.btnBack);
            btnLeft = findViewById(R.id.btnLeft);
            btnRight = findViewById(R.id.btnRight);
            btnStop = findViewById(R.id.btnStop);
            btnAuto = findViewById(R.id.btnAuto);
            btnMan = findViewById(R.id.btnMan);
            btnBodyActivate = findViewById(R.id.btnBodyActivate);
            btnMic = findViewById(R.id.btnMic);
            btnConfirmYes = findViewById(R.id.btnConfirmYes);
            btnConfirmNo = findViewById(R.id.btnConfirmNo);

            sliderSensitivity = findViewById(R.id.sliderSensitivity);

            layoutLine = findViewById(R.id.layoutLine);
            layoutClap = findViewById(R.id.layoutClap);
            layoutMusic = findViewById(R.id.layoutMusic);
            layoutDraw = findViewById(R.id.layoutDraw);

            previewLine = findViewById(R.id.previewLine);
            tvLineStatus = findViewById(R.id.tvLineStatus);
            sliderLineThreshold = findViewById(R.id.sliderLineThreshold);
            btnLineStart = findViewById(R.id.btnLineStart);

            btnClapMic = findViewById(R.id.btnClapMic);
            tvClapStatus = findViewById(R.id.tvClapStatus);
            tvClapCount = findViewById(R.id.tvClapCount);

            btnMusicStart = findViewById(R.id.btnMusicStart);
            tvMusicStatus = findViewById(R.id.tvMusicStatus);
            visualizerView = findViewById(R.id.visualizerView);

            btnDrawPlay = findViewById(R.id.btnDrawPlay);
            btnDrawClear = findViewById(R.id.btnDrawClear);
            tvDrawStatus = findViewById(R.id.tvDrawStatus);

            btnNavLine = findViewById(R.id.btnNavLine);
            btnNavClap = findViewById(R.id.btnNavClap);
            btnNavMusic = findViewById(R.id.btnNavMusic);
            btnNavDraw = findViewById(R.id.btnNavDraw);

            // Setup DrawPathView dynamically
            FrameLayout drawContainer = findViewById(R.id.drawPathContainer);
            if (drawContainer != null) {
                drawPathView = new DrawPathView(this);
                drawContainer.addView(drawPathView,
                    new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in initUI", e);
            Toast.makeText(this, "UI Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // LISTENERS
    // ==========================================
    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        btnOpenBluetooth.setOnClickListener(v -> showBluetoothDialog());

        btnNavManual.setOnClickListener(v -> switchMode("Manual"));
        btnNavBody.setOnClickListener(v -> switchMode("Body"));
        btnNavGyro.setOnClickListener(v -> switchMode("Gyro"));
        btnNavVoice.setOnClickListener(v -> switchMode("Voice"));
        btnNavLine.setOnClickListener(v -> switchMode("Line"));
        btnNavClap.setOnClickListener(v -> switchMode("Clap"));
        btnNavMusic.setOnClickListener(v -> switchMode("Music"));
        btnNavDraw.setOnClickListener(v -> switchMode("Draw"));

        View.OnTouchListener dpad = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int id = v.getId();
                if (id == R.id.btnFwd) sendCommand("F");
                else if (id == R.id.btnBack) sendCommand("B");
                else if (id == R.id.btnLeft) sendCommand("L");
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
        btnMan.setOnClickListener(v -> sendCommand("M"));

        btnBodyActivate.setOnClickListener(v -> {
            sendCommand("PAT");
            lblBodyStatus.setVisibility(View.VISIBLE);
        });

        sliderSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { threshold = p; }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnMic.setOnClickListener(v -> {
            if (isListening) stopListening();
            else startListening();
        });

        btnConfirmYes.setOnClickListener(v -> {
            if (!pendingIntent.isEmpty()) {
                sendCommand(pendingIntent);
                showVoiceResult(">>> COMMAND SENT: " + intentLabel(pendingIntent), true);
            }
            layoutConfirm.setVisibility(View.GONE);
            pendingIntent = "";
        });

        btnConfirmNo.setOnClickListener(v -> {
            layoutConfirm.setVisibility(View.GONE);
            pendingIntent = "";
            lblVoiceStatus.setText(">>> PRESS TO SPEAK");
            lblRecognizedText.setText("");
            lblConfidence.setText("");
        });

        sliderLineThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { lineThreshold = p; }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        btnLineStart.setOnClickListener(v -> {
            if (isLineTracking) {
                stopLineTracking();
                btnLineStart.setText("START TRACKING");
                btnLineStart.setBackgroundTintList(getResources().getColorStateList(R.color.status_green, null));
            } else {
                startLineTracking();
                btnLineStart.setText("STOP TRACKING");
                btnLineStart.setBackgroundTintList(getResources().getColorStateList(R.color.status_red, null));
            }
        });

        btnClapMic.setOnClickListener(v -> {
            if (isClapActive) stopClapListener();
            else startClapListener();
        });

        btnMusicStart.setOnClickListener(v -> {
            if (isMusicActive) stopMusicListener();
            else startMusicListener();
        });

        btnDrawPlay.setOnClickListener(v -> playDrawPath());
        btnDrawClear.setOnClickListener(v -> {
            if (drawPathView != null) drawPathView.clearPath();
            tvDrawStatus.setText(">>> DRAW A PATH WITH YOUR FINGER");
        });
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null)
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.CAMERA);

        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]),
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                             @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void initVoiceEngine() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle p) {
                lblVoiceStatus.setText(">>> LISTENING...");
                btnMic.setBackgroundTintList(getResources().getColorStateList(R.color.status_green, null));
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {
                isListening = false;
                btnMic.setBackgroundTintList(getResources().getColorStateList(R.color.button_dark, null));
                lblVoiceStatus.setText(">>> PROCESSING...");
            }
            @Override public void onError(int error) {
                isListening = false;
                btnMic.setBackgroundTintList(getResources().getColorStateList(R.color.button_dark, null));
                lblVoiceStatus.setText(">>> ERROR — TRY AGAIN");
                lblRecognizedText.setText("");
                lblConfidence.setText("");
            }
            @Override public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        speechRecognizer.startListening(intent);
    }

    private void stopListening() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        isListening = false;
        btnMic.setBackgroundTintList(getResources().getColorStateList(R.color.button_dark, null));
        lblVoiceStatus.setText(">>> PRESS TO SPEAK");
    }

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
            sendCommand(best);
            showVoiceResult(">>> COMMAND: " + intentLabel(best), true);
        } else if (bestScore >= 0.4) {
            pendingIntent = best;
            lblConfirmQuestion.setText("Did you mean: " + intentLabel(best) + " ?");
            layoutConfirm.setVisibility(View.VISIBLE);
            lblVoiceStatus.setText(">>> CONFIRM COMMAND");
        } else {
            showVoiceResult(">>> NOT RECOGNIZED", false);
        }
    }

    private void showVoiceResult(String status, boolean accepted) {
        lblVoiceStatus.setText(status);
        lblVoiceStatus.setTextColor(accepted ? getColor(R.color.status_green) : getColor(R.color.status_red));
        handler.postDelayed(() -> {
            lblVoiceStatus.setText(">>> PRESS TO SPEAK");
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
            default: return cmd;
        }
    }

    private String normalizeText(String input) {
        return input.toLowerCase().replaceAll("[^a-zA-Z\\u0600-\\u06FF ]", "").replaceAll("\\s+", " ").trim();
    }

    private double matchScore(String input, List<String> dict) {
        double best = 0;
        for (String word : dict) {
            if (input.equals(word)) return 1.0;
            if (input.contains(word)) best = Math.max(best, 0.7);
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

    private void showBiometricAuth(BluetoothDevice device) {
        pendingAuthDevice = device;
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthStrong = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        int canAuthWeak = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);

        if (canAuthStrong != BiometricManager.BIOMETRIC_SUCCESS &&
            canAuthWeak != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric not available. Enroll in Settings.", Toast.LENGTH_LONG).show();
            pendingAuthDevice = null;
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
            new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Toast.makeText(getApplicationContext(), "Auth error: " + errString, Toast.LENGTH_SHORT).show();
                    pendingAuthDevice = null;
                }
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    if (pendingAuthDevice != null) {
                        connectToDevice(pendingAuthDevice);
                        if (bluetoothDialog != null && bluetoothDialog.isShowing()) {
                            bluetoothDialog.dismiss();
                        }
                        pendingAuthDevice = null;
                    }
                }
                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(getApplicationContext(), "Auth failed", Toast.LENGTH_SHORT).show();
                    pendingAuthDevice = null;
                }
            });

        BiometricPrompt.PromptInfo.Builder promptBuilder = new BiometricPrompt.PromptInfo.Builder()
            .setTitle("Smart Car Security")
            .setSubtitle("Verify your identity")
            .setDescription("Use fingerprint or face");
        if (canAuthStrong == BiometricManager.BIOMETRIC_SUCCESS) {
            promptBuilder.setNegativeButtonText("Cancel");
        } else {
            promptBuilder.setDeviceCredentialAllowed(true);
        }
        biometricPrompt.authenticate(promptBuilder.build());
    }

    @SuppressLint("MissingPermission")
    private void showBluetoothDialog() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        bluetoothDialog = new Dialog(this);
        bluetoothDialog.setContentView(R.layout.dialog_bluetooth_devices);

        ListView lvDevices = bluetoothDialog.findViewById(R.id.lvDevices);
        Button btnDisconnect = bluetoothDialog.findViewById(R.id.btnDisconnect);

        Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
        List<String> nameList = new ArrayList<>();
        List<BluetoothDevice> devList = new ArrayList<>();
        for (BluetoothDevice d : paired) {
            nameList.add(d.getName() + "\n" + d.getAddress());
            devList.add(d);
        }

        lvDevices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nameList));
        lvDevices.setOnItemClickListener((p, v, pos, id) -> showBiometricAuth(devList.get(pos)));
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
                isConnected = true;
                runOnUiThread(() -> {
                    tvStatusIndicator.setTextColor(Color.parseColor("#00F0FF"));
                    Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                isConnected = false;
                runOnUiThread(() -> Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show());
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
        runOnUiThread(() -> tvStatusIndicator.setTextColor(Color.parseColor("#FF2A6D")));
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

    private void switchMode(String mode) {
        sendCommand("S");
        layoutManual.setVisibility(View.GONE);
        layoutBody.setVisibility(View.GONE);
        layoutGyro.setVisibility(View.GONE);
        layoutVoice.setVisibility(View.GONE);
        layoutLine.setVisibility(View.GONE);
        layoutClap.setVisibility(View.GONE);
        layoutMusic.setVisibility(View.GONE);
        layoutDraw.setVisibility(View.GONE);

        isGyroActive = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (isListening) stopListening();
        if (isLineTracking) stopLineTracking();
        if (isClapActive) stopClapListener();
        if (isMusicActive) stopMusicListener();

        switch (mode) {
            case "Manual":
                layoutManual.setVisibility(View.VISIBLE);
                tvModeLabel.setText(">>> MODE: MANUAL");
                break;
            case "Body":
                layoutBody.setVisibility(View.VISIBLE);
                lblBodyStatus.setVisibility(View.INVISIBLE);
                tvModeLabel.setText(">>> MODE: BODY FOLLOWER");
                sendCommand("PAT");
                break;
            case "Gyro":
                sendCommand("GYR");
                layoutGyro.setVisibility(View.VISIBLE);
                tvModeLabel.setText(">>> MODE: GYRO");
                isGyroActive = true;
                if (sensorManager != null && accelerometer != null)
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                break;
            case "Voice":
                sendCommand("M");
                layoutVoice.setVisibility(View.VISIBLE);
                tvModeLabel.setText(">>> MODE: VOICE AI");
                layoutConfirm.setVisibility(View.GONE);
                lblVoiceStatus.setText(">>> PRESS TO SPEAK");
                lblRecognizedText.setText("");
                lblConfidence.setText("");
                break;
            case "Line":
                sendCommand("LIN");
                layoutLine.setVisibility(View.VISIBLE);
                tvModeLabel.setText(">>> MODE: LINE FOLLOWER");
                tvLineStatus.setText("// >>> POINT CAMERA AT LINE");
                break;
            case "Clap":
                sendCommand("CLP");
                layoutClap.setVisibility(View.VISIBLE);
                tvModeLabel.setText(">>> MODE: CLAP CONTROL");
                tvClapStatus.setText(">>> TAP TO ACTIVATE");
                tvClapCount.setText("");
                break;
            case "Music":
                sendCommand("MUS");
                layoutMusic.setVisibility(View.VISIBLE);
                tvModeLabel.setText(">>> MODE: MUSIC RHYTHM");
                tvMusicStatus.setText(">>> TAP TO START");
                break;
            case "Draw":
                sendCommand("DRW");
                layoutDraw.setVisibility(View.VISIBLE);
                tvModeLabel.setText(">>> MODE: DRAW PATH");
                tvDrawStatus.setText(">>> DRAW A PATH WITH YOUR FINGER");
                break;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isGyroActive) return;
        r1x = r2x; r2x = r3x; r3x = event.values[0];
        r1y = r2y; r2y = r3y; r3y = event.values[1];
        float avgX = (r1x + r2x + r3x) / 3f;
        float avgY = (r1y + r2y + r3y) / 3f;
        float thr = threshold / 5.0f;
        String target, status;
        if (avgY > thr) { target = "F"; status = "Forward"; }
        else if (avgY < -thr) { target = "B"; status = "Backward"; }
        else if (avgX > thr) { target = "R"; status = "Right"; }
        else if (avgX < -thr) { target = "L"; status = "Left"; }
        else { target = "S"; status = "Stop / Flat";}
        lblGyroStatus.setText(status);
        if (!target.equals(lastCmd)) sendCommand(target);
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    // ==========================================
    // LINE FOLLOWER
    // ==========================================
    private void startLineTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        isLineTracking = true;
        tvLineStatus.setText("// >>> TRACKING ACTIVE");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
            ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewLine.getSurfaceProvider());
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                imageAnalysis.setAnalyzer(cameraExecutor, new LineAnalyzer());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> tvLineStatus.setText("// CAMERA ERROR"));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopLineTracking() {
        isLineTracking = false;
        tvLineStatus.setText("// >>> POINT CAMERA AT LINE");
        if (cameraProvider != null) cameraProvider.unbindAll();
    }

    private class LineAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            if (!isLineTracking) { image.close(); return; }
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            int width = image.getWidth();
            int height = image.getHeight();
            int rowStride = image.getPlanes()[0].getRowStride();
            int startY = height * 6 / 10;
            int sumX = 0;
            int count = 0;
            for (int y = startY; y < height; y += 2) {
                for (int x = 0; x < width; x += 4) {
                    int gray = yBuffer.get(y * rowStride + x) & 0xFF;
                    if (gray < lineThreshold) { sumX += x; count++; }
                }
            }
            if (count > 50) {
                int centerX = sumX / count;
                int imageCenter = width / 2;
                int tolerance = width / 8;
                final String cmd;
                final String status;
                if (centerX < imageCenter - tolerance) { cmd = "L"; status = ">>> LINE LEFT"; }
                else if (centerX > imageCenter + tolerance) { cmd = "R"; status = ">>> LINE RIGHT"; }
                else { cmd = "F"; status = ">>> LINE CENTER"; }
                runOnUiThread(() -> { sendCommand(cmd); tvLineStatus.setText("// " + status); });
            } else {
                runOnUiThread(() -> { sendCommand("S"); tvLineStatus.setText("// >>> NO LINE DETECTED"); });
            }
            image.close();
        }
    }

    // ==========================================
    // CLAP CONTROL
    // ==========================================
    private void startClapListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        isClapActive = true;
        btnClapMic.setBackgroundTintList(getResources().getColorStateList(R.color.status_green, null));
        tvClapStatus.setText(">>> LISTENING...");
        clapCount = 0;
        clapWindowStart.set(0);
        windowClaps.set(0);

        final int sampleRate = 44100;
        final int bufferSize = Math.max(1024,
            AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
        clapAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        final short[] buffer = new short[bufferSize];
        clapAudioRecord.startRecording();
        clapThread = new Thread(new ClapRunnable(buffer, bufferSize));
        clapThread.start();
    }

    private class ClapRunnable implements Runnable {
        private final short[] buffer;
        private final int bufSize;
        ClapRunnable(short[] buffer, int bufSize) { this.buffer = buffer; this.bufSize = bufSize; }
        @Override
        public void run() {
            while (isClapActive && clapAudioRecord != null) {
                int read = clapAudioRecord.read(buffer, 0, bufSize);
                if (read <= 0) continue;
                double rms = calculateRMS(buffer, read);
                if (rms > CLAP_THRESHOLD) {
                    long now = System.currentTimeMillis();
                    if (now - lastClapTime > 250) {
                        lastClapTime = now;
                        long currentWindowStart = clapWindowStart.get();
                        if (currentWindowStart == 0 || now - currentWindowStart > 1200) {
                            clapWindowStart.set(now);
                            windowClaps.set(1);
                        } else {
                            windowClaps.incrementAndGet();
                        }
                        final int currentWindowClaps = windowClaps.get();
                        runOnUiThread(() -> tvClapCount.setText("CLAPS: " + currentWindowClaps));
                        if (windowClaps.get() >= 3) {
                            executeClapCommand(windowClaps.get());
                            clapWindowStart.set(0);
                            windowClaps.set(0);
                        } else {
                            final long windowStartSnapshot = clapWindowStart.get();
                            handler.postDelayed(() -> {
                                if (isClapActive && System.currentTimeMillis() - windowStartSnapshot >= 1000) {
                                    executeClapCommand(currentWindowClaps);
                                }
                            }, 1100);
                        }
                    }
                }
            }
        }
    }

    private void stopClapListener() {
        isClapActive = false;
        btnClapMic.setBackgroundTintList(getResources().getColorStateList(R.color.button_dark, null));
        tvClapStatus.setText(">>> TAP TO ACTIVATE");
        tvClapCount.setText("");
        if (clapAudioRecord != null) { clapAudioRecord.stop(); clapAudioRecord.release(); clapAudioRecord = null; }
    }

    private void executeClapCommand(int claps) {
        String cmd, label;
        switch (claps) {
            case 1: cmd = "S"; label = "STOP"; break;
            case 2: cmd = "F"; label = "FORWARD"; break;
            case 3: cmd = "B"; label = "BACKWARD"; break;
            default: cmd = "S"; label = "STOP"; break;
        }
        sendCommand(cmd);
        runOnUiThread(() -> tvClapStatus.setText("// " + label + " (" + claps + " claps)"));
    }

    // ==========================================
    // MUSIC RHYTHM
    // ==========================================
    private void startMusicListener() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            return;
        }
        isMusicActive = true;
        btnMusicStart.setBackgroundTintList(getResources().getColorStateList(R.color.status_green, null));
        tvMusicStatus.setText(">>> LISTENING...");
        final int sampleRate = 44100;
        final int bufferSize = Math.max(2048,
            AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
        musicAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        final short[] buffer = new short[bufferSize];
        musicAudioRecord.startRecording();
        musicThread = new Thread(new MusicRunnable(buffer, bufferSize));
        musicThread.start();
    }

    private class MusicRunnable implements Runnable {
        private final short[] buffer;
        private final int bufSize;
        MusicRunnable(short[] buffer, int bufSize) { this.buffer = buffer; this.bufSize = bufSize; }
        @Override
        public void run() {
            double prevEnergy = 0;
            int beatCount = 0;
            while (isMusicActive && musicAudioRecord != null) {
                int read = musicAudioRecord.read(buffer, 0, bufSize);
                if (read <= 0) continue;
                double energy = calculateRMS(buffer, read);
                double flux = energy - prevEnergy;
                prevEnergy = energy;
                final float barHeight = Math.min(60f, (float)(energy / 100));
                runOnUiThread(() -> {
                    visualizerView.setScaleY(barHeight / 60f);
                    visualizerView.setAlpha(0.5f + (barHeight / 120f));
                });
                if (flux > 1500 && energy > 2000) {
                    beatCount++;
                    final int currentBeat = beatCount;
                    runOnUiThread(() -> {
                        tvMusicStatus.setText("// >>> BEAT #" + currentBeat);
                        if (currentBeat % 4 == 0) {
                            sendCommand("R");
                            handler.postDelayed(() -> sendCommand("L"), 200);
                            handler.postDelayed(() -> sendCommand("F"), 400);
                        } else {
                            sendCommand("F");
                        }
                    });
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void stopMusicListener() {
        isMusicActive = false;
        btnMusicStart.setBackgroundTintList(getResources().getColorStateList(R.color.button_dark, null));
        tvMusicStatus.setText(">>> TAP TO START");
        visualizerView.setScaleY(1f);
        visualizerView.setAlpha(1f);
        if (musicAudioRecord != null) { musicAudioRecord.stop(); musicAudioRecord.release(); musicAudioRecord = null; }
    }

    // ==========================================
    // DRAW PATH
    // ==========================================
    private void playDrawPath() {
        if (drawPathView == null) return;
        List<PointF> points = drawPathView.getPoints();
        if (points.size() < 2) {
            tvDrawStatus.setText("DRAW SOMETHING FIRST!");
            return;
        }
        if (isDrawingPlaying) return;
        isDrawingPlaying = true;
        tvDrawStatus.setText("// PLAYING...");
        new Thread(() -> {
            int step = Math.max(1, points.size() / 50);
            List<PointF> simplified = new ArrayList<>();
            for (int i = 0; i < points.size(); i += step) simplified.add(points.get(i));
            if (simplified.size() < 2) simplified = points;
            for (int i = 1; i < simplified.size(); i++) {
                PointF prev = simplified.get(i - 1);
                PointF curr = simplified.get(i);
                float dx = curr.x - prev.x;
                float dy = curr.y - prev.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                float adjusted = angle + 90;
                if (adjusted > 180) adjusted -= 360;
                if (adjusted < -180) adjusted += 360;
                String cmd;
                int duration;
                if (Math.abs(adjusted) < 25) { cmd = "F"; duration = (int)(dist * 8); }
                else if (adjusted > 25) { cmd = "R"; duration = (int)(Math.abs(adjusted) * 6); }
                else { cmd = "L"; duration = (int)(Math.abs(adjusted) * 6); }
                final String fCmd = cmd;
                final int fDur = Math.min(duration, 800);
                final int progress = i;
                final int total = simplified.size();
                runOnUiThread(() -> {
                    sendCommand(fCmd);
                    tvDrawStatus.setText("// >>> STEP " + progress + "/" + total + " -> " + fCmd);
                });
                try { Thread.sleep(fDur); } catch (InterruptedException ignored) {}
            }
            runOnUiThread(() -> {
                sendCommand("S");
                tvDrawStatus.setText("// COMPLETE");
                isDrawingPlaying = false;
            });
        }).start();
    }

    private double calculateRMS(short[] buffer, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) sum += buffer[i] * buffer[i];
        return Math.sqrt(sum / length);
    }

    // ==========================================
    // CUSTOM DRAW PATH VIEW
    // ==========================================
    public static class DrawPathView extends View {
        private Path path = new Path();
        private Paint paint;
        private List<PointF> points = new ArrayList<>();

        public DrawPathView(Context context) {
            super(context);
            init();
        }
        public DrawPathView(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            init();
        }
        private void init() {
            paint = new Paint();
            paint.setColor(Color.parseColor("#00F0FF"));
            paint.setStrokeWidth(8f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setAntiAlias(true);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.parseColor("#1a1a2e"));
            if (!path.isEmpty()) canvas.drawPath(path, paint);
        }
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path.moveTo(x, y);
                    points.add(new PointF(x, y));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    path.lineTo(x, y);
                    points.add(new PointF(x, y));
                    invalidate();
                    return true;
            }
            return false;
        }
        public void clearPath() {
            path.reset();
            points.clear();
            invalidate();
        }
        public List<PointF> getPoints() {
            return new ArrayList<>(points);
        }
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handleDisconnect();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (speechRecognizer != null) { speechRecognizer.stopListening(); speechRecognizer.destroy(); }
        if (isLineTracking) stopLineTracking();
        if (isClapActive) stopClapListener();
        if (isMusicActive) stopMusicListener();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
