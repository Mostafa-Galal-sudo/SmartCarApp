package com.example.smartcar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // UI Elements
    private TextView tvModeLabel, tvStatusIndicator, lblBodyStatus, lblGyroStatus;
    private View layoutManual, layoutBody, layoutGyro;
    private Button btnOpenBluetooth, btnNavManual, btnNavBody, btnNavGyro;
    private Button btnFwd, btnBack, btnLeft, btnRight, btnStop, btnAuto, btnMan, btnBodyActivate;
    private SeekBar sliderSensitivity;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean isConnected = false;
    private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private Dialog bluetoothDialog;

    // Command Logic
    private String lastCmd = "";
    private boolean cmdDelayActive = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Gyro
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isGyroActive = false;
    private float threshold = 15f;

    // Rolling averages
    private float r1x = 0, r2x = 0, r3x = 0;
    private float r1y = 0, r2y = 0, r3y = 0;

    // Safety Timer
    private Runnable connectionChecker;

    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        setupListeners();
        setupSensors();
        checkPermissions();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Connection checker loop
        connectionChecker = new Runnable() {
            @Override
            public void run() {
                if (isConnected && bluetoothSocket != null && !bluetoothSocket.isConnected()) {
                    handleDisconnect();
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(connectionChecker);
    }

    private void initUI() {
        tvModeLabel = findViewById(R.id.tvModeLabel);
        tvStatusIndicator = findViewById(R.id.tvStatusIndicator);
        lblBodyStatus = findViewById(R.id.lblBodyStatus);
        lblGyroStatus = findViewById(R.id.lblGyroStatus);

        layoutManual = findViewById(R.id.layoutManual);
        layoutBody = findViewById(R.id.layoutBody);
        layoutGyro = findViewById(R.id.layoutGyro);

        btnOpenBluetooth = findViewById(R.id.btnOpenBluetooth);
        btnNavManual = findViewById(R.id.btnNavManual);
        btnNavBody = findViewById(R.id.btnNavBody);
        btnNavGyro = findViewById(R.id.btnNavGyro);

        btnFwd = findViewById(R.id.btnFwd);
        btnBack = findViewById(R.id.btnBack);
        btnLeft = findViewById(R.id.btnLeft);
        btnRight = findViewById(R.id.btnRight);
        btnStop = findViewById(R.id.btnStop);
        btnAuto = findViewById(R.id.btnAuto);
        btnMan = findViewById(R.id.btnMan);
        btnBodyActivate = findViewById(R.id.btnBodyActivate);

        sliderSensitivity = findViewById(R.id.sliderSensitivity);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        btnOpenBluetooth.setOnClickListener(v -> showBluetoothDialog());

        // Nav
        btnNavManual.setOnClickListener(v -> switchMode("Manual"));
        btnNavBody.setOnClickListener(v -> switchMode("Body"));
        btnNavGyro.setOnClickListener(v -> switchMode("Gyro"));

        // Manual Controls
        View.OnTouchListener dpadListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (v.getId() == R.id.btnFwd)
                    sendCommand("F");
                else if (v.getId() == R.id.btnBack)
                    sendCommand("B");
                else if (v.getId() == R.id.btnLeft)
                    sendCommand("L");
                else if (v.getId() == R.id.btnRight)
                    sendCommand("R");
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                sendCommand("S");
            }
            return false;
        };

        btnFwd.setOnTouchListener(dpadListener);
        btnBack.setOnTouchListener(dpadListener);
        btnLeft.setOnTouchListener(dpadListener);
        btnRight.setOnTouchListener(dpadListener);

        btnStop.setOnClickListener(v -> sendCommand("S"));
        btnAuto.setOnClickListener(v -> sendCommand("A"));
        btnMan.setOnClickListener(v -> sendCommand("M"));

        // Body Follower
        btnBodyActivate.setOnClickListener(v -> {
            sendCommand("PAT");
            lblBodyStatus.setVisibility(View.VISIBLE);
        });

        // Gyro Slider
        sliderSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                threshold = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, PERMISSION_REQUEST_CODE);
            }
        }
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

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<String> deviceList = new ArrayList<>();
        List<BluetoothDevice> devices = new ArrayList<>();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceList.add(device.getName() + "\n" + device.getAddress());
                devices.add(device);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        lvDevices.setAdapter(adapter);

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            connectToDevice(devices.get(position));
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
                isConnected = true;

                runOnUiThread(() -> {
                    tvStatusIndicator.setTextColor(Color.GREEN);
                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                isConnected = false;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection Failed", Toast.LENGTH_SHORT).show());
                try {
                    if (bluetoothSocket != null)
                        bluetoothSocket.close();
                } catch (IOException ignored) {
                }
            }
        }).start();
    }

    private void handleDisconnect() {
        isConnected = false;
        sendCommand("S");
        try {
            if (bluetoothSocket != null)
                bluetoothSocket.close();
        } catch (IOException ignored) {
        }
        runOnUiThread(() -> tvStatusIndicator.setTextColor(Color.RED));
    }

    private void sendCommand(String cmd) {
        if (!isConnected || outputStream == null)
            return;
        if (cmdDelayActive)
            return; // Enforce 80ms delay

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
        isGyroActive = false;

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        switch (mode) {
            case "Manual":
                layoutManual.setVisibility(View.VISIBLE);
                tvModeLabel.setText("Mode: Manual");
                break;
            case "Body":
                layoutBody.setVisibility(View.VISIBLE);
                tvModeLabel.setText("Mode: Body Follower");
                lblBodyStatus.setVisibility(View.INVISIBLE);
                break;
            case "Gyro":
                sendCommand("GYR");
                layoutGyro.setVisibility(View.VISIBLE);
                tvModeLabel.setText("Mode: Gyro Control");
                isGyroActive = true;
                if (sensorManager != null && accelerometer != null) {
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                }
                break;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isGyroActive)
            return;

        // Shift rolling average
        r1x = r2x;
        r2x = r3x;
        r3x = event.values[0];
        r1y = r2y;
        r2y = r3y;
        r3y = event.values[1];

        float avgX = (r1x + r2x + r3x) / 3f;
        float avgY = (r1y + r2y + r3y) / 3f;

        // Map slider (0-40) to threshold (0.0 - 8.0 m/s^2)
        float effectiveThreshold = threshold / 5.0f;

        String targetCmd = "";
        String statusText = "";

        if (avgY > effectiveThreshold) {
            targetCmd = "F";
            statusText = "Forward";
        } else if (avgY < -effectiveThreshold) {
            targetCmd = "B";
            statusText = "Backward";
        } else if (avgX > effectiveThreshold) {
            targetCmd = "R";
            statusText = "Right";
        } else if (avgX < -effectiveThreshold) {
            targetCmd = "L";
            statusText = "Left";
        } else {
            targetCmd = "S";
            statusText = "Stop / Flat";
        }

        lblGyroStatus.setText(statusText);

        if (!targetCmd.equals(lastCmd)) {
            sendCommand(targetCmd);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handleDisconnect();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}
