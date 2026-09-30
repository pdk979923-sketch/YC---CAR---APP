package com.yc.car;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("YC CAR CONTROLLER");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView status = new TextView(this);
        status.setText("\nBluetooth controller ready\n");
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button bluetooth = new Button(this);
        bluetooth.setText("CHECK BLUETOOTH");
        root.addView(bluetooth, new LinearLayout.LayoutParams(-1, -2));

        String[] controls = {"FORWARD", "BACK", "LEFT", "RIGHT", "STOP"};
        for (String c : controls) {
            Button b = new Button(this);
            b.setText(c);
            root.addView(b, new LinearLayout.LayoutParams(-1, -2));
            b.setOnClickListener(v ->
                Toast.makeText(this, c + " pressed", Toast.LENGTH_SHORT).show()
            );
        }

        setContentView(root);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetooth.setOnClickListener(v -> {
            if (bluetoothAdapter == null) {
                status.setText("Bluetooth is not supported on this phone.");
                return;
            }
            if (!bluetoothAdapter.isEnabled()) {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                status.setText("Bluetooth is ON.\nPair/connect your YC car.");
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            }, 100);
        }
    }
}
