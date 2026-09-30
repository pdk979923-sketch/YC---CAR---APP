package com.yc.carcontroller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_BT = 10;
    private static final UUID FFF2 = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb");
    private static final UUID FFF0 = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");

    // Reverse-engineered YC_CAR_DEMO packets, sent repeatedly while a button is held.
    private static final byte[] FORWARD = hex("AA000200000000420002");
    private static final byte[] BACK = hex("AA000200000000410002");
    private static final byte[] LEFT = hex("AA000200000000440002");
    private static final byte[] RIGHT = hex("AA000200000000480002");
    private static final byte[] STOP = hex("AA000200000000400002");

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String,BluetoothDevice> devices = new LinkedHashMap<>();
    private ArrayAdapter<String> deviceAdapter;
    private ListView list;
    private TextView status;
    private boolean scanning = false;
    private byte[] active = STOP;

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            if (writeChar != null && gatt != null) {
                try {
                    writeChar.setValue(active);
                    writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    gatt.writeCharacteristic(writeChar);
                } catch (Exception ignored) {}
                handler.postDelayed(this, 100);
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        BluetoothManager bm = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm.getAdapter();
        if (adapter == null) {
            status.setText("This phone does not support Bluetooth LE.");
            return;
        }
        requestBt();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,18,18,18);

        TextView title = new TextView(this);
        title.setText("YC CAR CONTROLLER");
        title.setTextSize(24);
        title.setGravity(17);
        title.setPadding(0,8,0,8);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Ready — scan for your car");
        status.setTextSize(16);
        status.setPadding(0,4,0,10);
        root.addView(status);

        Button scan = new Button(this);
        scan.setText("SCAN / REFRESH");
        scan.setOnClickListener(v -> startScan());
        root.addView(scan);

        list = new ListView(this);
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(deviceAdapter);
        list.setOnItemClickListener((p,v,pos,id) -> {
            String key = deviceAdapter.getItem(pos);
            BluetoothDevice d = devices.get(key);
            if (d != null) connect(d);
        });
        root.addView(list, new LinearLayout.LayoutParams(-1,0,1));

        TextView hint = new TextView(this);
        hint.setText("Tap your YC/YC_CAR device above. Then hold a direction button.");
        hint.setTextSize(14);
        root.addView(hint);

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        addButtonRow(pad, new String[]{"FORWARD"}, new View.OnTouchListener[]{hold(FORWARD)});
        addButtonRow(pad, new String[]{"LEFT","STOP","RIGHT"},
                new View.OnTouchListener[]{hold(LEFT), hold(STOP), hold(RIGHT)});
        addButtonRow(pad, new String[]{"REVERSE"}, new View.OnTouchListener[]{hold(BACK)});
        root.addView(pad, new LinearLayout.LayoutParams(-1,-2));

        Button disconnect = new Button(this);
        disconnect.setText("DISCONNECT");
        disconnect.setOnClickListener(v -> disconnect());
        root.addView(disconnect);

        setContentView(root);
    }

    private void addButtonRow(LinearLayout parent, String[] labels, View.OnTouchListener[] listeners) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(17);
        for (int i=0;i<labels.length;i++) {
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setTextSize(15);
            b.setOnTouchListener(listeners[i]);
            row.addView(b, new LinearLayout.LayoutParams(0, 58, 1));
        }
        parent.addView(row);
    }

    private View.OnTouchListener hold(byte[] cmd) {
        return (v,e) -> {
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                active = cmd;
                handler.removeCallbacks(repeat);
                handler.post(repeat);
                return true;
            } else if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                active = STOP;
                sendOnce(STOP);
                handler.removeCallbacks(repeat);
                return true;
            }
            return true;
        };
    }

    private void requestBt() {
        if (Build.VERSION.SDK_INT >= 31) {
            ArrayList<String> p = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)
                p.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
                p.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ_BT);
        }
    }

    private void startScan() {
        if (!adapter.isEnabled()) {
            status.setText("Please turn Bluetooth ON.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED) {
            requestBt(); return;
        }
        devices.clear();
        deviceAdapter.clear();
        deviceAdapter.notifyDataSetChanged();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { status.setText("BLE scanner unavailable."); return; }
        scanning = true;
        status.setText("Scanning… turn the car ON.");
        scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
        handler.postDelayed(() -> stopScan(), 10000);
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        if (scanner != null) {
            try { scanner.stopScan(scanCallback); } catch(Exception ignored) {}
        }
        status.setText(devices.isEmpty() ? "No BLE device found. Keep car ON and scan again." :
                "Select your car from the list.");
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult r) {
            BluetoothDevice d = r.getDevice();
            String name = "";
            try { name = d.getName(); } catch(Exception ignored) {}
            if (name == null || name.trim().isEmpty()) name = "Unnamed BLE device";
            String key = name + "\n" + d.getAddress();
            if (!devices.containsKey(key)) {
                devices.put(key,d);
                runOnUiThread(() -> { deviceAdapter.add(key); deviceAdapter.notifyDataSetChanged(); });
            }
        }
    };

    private void connect(BluetoothDevice d) {
        stopScan();
        disconnect();
        status.setText("Connecting to " + safeName(d) + "…");
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            requestBt(); return;
        }
        gatt = d.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int s, int newState) {
            runOnUiThread(() -> status.setText(newState == BluetoothGatt.STATE_CONNECTED ?
                    "Connected. Finding control characteristic…" : "Disconnected."));
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                try { g.discoverServices(); } catch(Exception ignored) {}
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                writeChar = null;
                handler.removeCallbacks(repeat);
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int s) {
            writeChar = null;
            for (BluetoothGattService svc : g.getServices()) {
                for (BluetoothGattCharacteristic c : svc.getCharacteristics()) {
                    UUID u = c.getUuid();
                    if (u.equals(FFF2) || (u.toString().toLowerCase().contains("fff2") &&
                        (c.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE |
                                             BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0)) {
                        writeChar = c; break;
                    }
                }
                if (writeChar != null) break;
            }
            if (writeChar == null) {
                // Fallback: first writable characteristic.
                outer: for (BluetoothGattService svc : g.getServices()) {
                    for (BluetoothGattCharacteristic c : svc.getCharacteristics()) {
                        int p=c.getProperties();
                        if ((p & (BluetoothGattCharacteristic.PROPERTY_WRITE |
                                  BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                            writeChar=c; break outer;
                        }
                    }
                }
            }
            runOnUiThread(() -> status.setText(writeChar != null ?
                    "READY — hold a direction button." :
                    "Connected, but no writable BLE characteristic found."));
            if (writeChar != null) sendOnce(STOP);
        }
    };

    private void sendOnce(byte[] data) {
        if (gatt == null || writeChar == null) return;
        try {
            writeChar.setValue(data);
            int p=writeChar.getProperties();
            writeChar.setWriteType((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)!=0 ?
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE :
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            gatt.writeCharacteristic(writeChar);
        } catch(Exception ignored) {}
    }

    private void disconnect() {
        handler.removeCallbacks(repeat);
        active=STOP;
        if (gatt != null) {
            try { sendOnce(STOP); } catch(Exception ignored) {}
            try { gatt.disconnect(); } catch(Exception ignored) {}
            try { gatt.close(); } catch(Exception ignored) {}
        }
        gatt=null; writeChar=null;
    }

    private String safeName(BluetoothDevice d) {
        try { return d.getName()==null ? d.getAddress() : d.getName(); } catch(Exception e) { return "device"; }
    }

    private static byte[] hex(String s) {
        byte[] out=new byte[s.length()/2];
        for(int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return out;
    }

    @Override protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}
