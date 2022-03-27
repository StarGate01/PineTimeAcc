package de.chrz.pinetimeacc;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class BLEManager extends ScanCallback {

    private final BluetoothLeScanner scanner;
    private final List<ScanFilter> filters;
    private final ScanSettings settings;

    private final List<BLEDevice> devices;
    private BLEManagerChangedListener listener;

    static TaskRunner taskRunner = new TaskRunner();

    private static class ScanTask implements Callable<Void> {

        private final BluetoothLeScanner scanner;
        private final List<ScanFilter> filters;
        private final ScanSettings settings;
        private final ScanCallback callback;

        public ScanTask(BluetoothLeScanner scanner, List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
            this.scanner = scanner;
            this.filters = filters;
            this.settings = settings;
            this.callback = callback;
        }

        @Override
        public Void call() {
            if (scanner == null) return null;
            try {
                scanner.startScan(filters, settings, callback);
            } catch (SecurityException ignored) { }
            return null;
        }

    }

    public BLEManager() {
        devices = new ArrayList<>();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        filters = new ArrayList<>();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();
        scanner = adapter.getBluetoothLeScanner();
    }

    public List<BLEDevice> getDevices() {
        return devices;
    }

    public void setListener(BLEManagerChangedListener listener) {
        this.listener = listener;
    }

    public void beginScan() {
        taskRunner.executeAsync(new ScanTask(scanner, filters, settings, this), (result) -> { });
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        BluetoothDevice d = result.getDevice();
        try {
            if(d.getName() != null && !d.getName().isEmpty()) {
                BLEDevice device = new BLEDevice(d.getName(), d.getAddress(), d);
                if (!devices.contains(device)) {
                    devices.add(device);
                    Collections.sort(devices);
                    listener.deviceListUpdated();
                }
            }
        } catch (SecurityException ignored) { }
    }

}
