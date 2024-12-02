package com.example.ubicompproj;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String DEVICE_NAME = "BBC micro:bit [gegeg]"; // Adjust if your device has a different name
    private static final UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private TextView resultTextView;
    private Button startGameButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultTextView = findViewById(R.id.resultTextView);
        startGameButton = findViewById(R.id.startGameButton);

        startGameButton.setOnClickListener(v -> startGame());
    }

    private void startGame() {
        new Thread(() -> {
            try {
                setupBluetooth();
                listenForData();
            } catch (IOException e) {
                Log.e(TAG, "Error during Bluetooth setup or communication: " + e.getMessage(), e);
                runOnUiThread(() -> resultTextView.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void setupBluetooth() throws IOException {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            throw new IOException("Bluetooth not supported on this device.");
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            // Bluetooth is off, prompt the user to turn it on
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
            throw new IOException("Bluetooth is not enabled.");
        }

        // Wait for Bluetooth to be ready before attempting to connect
        while (!bluetoothAdapter.isEnabled()) {
            try {
                Thread.sleep(500); // Small delay to allow Bluetooth to turn on
            } catch (InterruptedException e) {
                Log.e(TAG, "Bluetooth enable wait interrupted.", e);
            }
        }

        BluetoothDevice microbitDevice = null;
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            Log.d(TAG, "Paired device: " + device.getName());
            if (DEVICE_NAME.equals(device.getName())) {
                microbitDevice = device;
                break;
            }
        }

        if (microbitDevice == null) {
            throw new IOException("Micro:bit not found.");
        }

        try {
            bluetoothSocket = microbitDevice.createRfcommSocketToServiceRecord(UART_SERVICE_UUID);
            Log.d(TAG, "Attempting to connect to micro:bit...");
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            Log.d(TAG, "Connected to micro:bit.");
            runOnUiThread(() -> resultTextView.setText("Connected to Micro:bit"));
        } catch (IOException e) {
            throw new IOException("Failed to connect to Micro:bit: " + e.getMessage());
        }
    }

    private void listenForData() throws IOException {
        byte[] buffer = new byte[1024];
        int bytes;

        while (true) {
            try {
                if (inputStream == null) break;
                bytes = inputStream.read(buffer);
                String data = new String(buffer, 0, bytes);
                Log.d(TAG, "Data received: " + data);
                runOnUiThread(() -> processReceivedData(data));
            } catch (IOException e) {
                Log.e(TAG, "Error reading data: " + e.getMessage());
                throw new IOException("Error reading data: " + e.getMessage());
            }
        }
    }

    private void processReceivedData(String data) {
        if (data.startsWith("ReactionTime:")) {
            String reactionTime = data.split(":")[1];
            resultTextView.setText("Reaction Time: " + reactionTime + " ms");
        } else if (data.startsWith("X") || data.startsWith("Y") || data.startsWith("Z")) {
            resultTextView.append("\n" + data);
        } else if (data.equals("GO")) {
            resultTextView.setText("GO! Shake the micro:bit.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing Bluetooth socket: " + e.getMessage());
        }
    }
}
