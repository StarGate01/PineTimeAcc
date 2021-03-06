package de.chrz.pinetimeacc.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import de.chrz.pinetimeacc.TaskRunner;
import de.chrz.pinetimeacc.sampling.Sample;

public class BLEManager extends ScanCallback implements BLEDeviceChangedListener {

    private final BluetoothLeScanner scanner;
    private final List<ScanFilter> filters;
    private final ScanSettings settings;

    private final List<BLEDevice> devices;
    private final List<BLEManagerChangedListener> listeners;

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

        listeners = new ArrayList<>();

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

    public BLEDevice getActiveDevice() {
        return devices.stream().filter(BLEDevice::isConnected).findFirst().orElse(null);
    }

    public void addListener(BLEManagerChangedListener listener) {
        if(!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(BLEManagerChangedListener listener) {
        try {
            listeners.remove(listener);
        } catch (Exception ignored) { }
    }

    private void invokeListUpdated() {
        for (BLEManagerChangedListener listener: listeners) {
            listener.deviceListUpdated();
        }
    }

    private void invokeDeviceUpdated(BLEDevice device) {
        for (BLEManagerChangedListener listener: listeners) {
            listener.individualDeviceUpdated(device);
        }
    }

    private void invokeDataIncoming(Sample[] data) {
        for (BLEManagerChangedListener listener: listeners) {
            listener.individualDataIncoming(data);
        }
    }

    public void beginScan() {
        taskRunner.executeAsync(new ScanTask(scanner, filters, settings, this), (result) -> { });
    }

    @Override
    public void deviceUpdated(BLEDevice device) {
        invokeDeviceUpdated(device);
    }

    @Override
    public void dataIncoming(Sample[] data) { invokeDataIncoming(data); }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        BluetoothDevice d = result.getDevice();
        try {
            if(d.getName() != null && !d.getName().isEmpty()) {
                BLEDevice device = new BLEDevice(d.getName(), d.getAddress(), d);
                if (!devices.contains(device)) {
                    devices.add(device);
                    device.addListener(this);
                    invokeListUpdated();
                }
            }
        } catch (SecurityException ignored) { }
    }

}
