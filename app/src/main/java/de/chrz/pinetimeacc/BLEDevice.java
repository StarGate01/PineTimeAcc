package de.chrz.pinetimeacc;

import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;


public class BLEDevice implements Comparable<BLEDevice> {
    private final String mName;
    private final String mAddress;
    private final BluetoothDevice mDevice;
    private boolean mConnected = false;
    private List<BLEDeviceChangedListener> listeners;

    static TaskRunner taskRunner = new TaskRunner();

    @Override
    public int compareTo(BLEDevice bleDevice) {
        return mName.compareTo(bleDevice.getName());
    }

    private static class ConnectTask implements Callable<Boolean> {
        private final boolean action;

        public ConnectTask(boolean action) {
            this.action = action;
        }

        @Override
        public Boolean call() throws Exception {
            Thread.sleep(2000);
            return action;
        }
    }

    public BLEDevice(String name, String address, BluetoothDevice device) {
        mName = name;
        mAddress = address;
        mDevice = device;

        listeners = new ArrayList<>();
    }

    public String getName() {
        return mName;
    }

    public String getAddress() {
        return mAddress;
    }

    public boolean isConnected() {
        return mConnected;
    }

    public void connect() {
        taskRunner.executeAsync(new ConnectTask(true), (result) -> {
           mConnected = result;
           invokeDeviceUpdated(this);
        });
    }

    public void disconnect() {
        taskRunner.executeAsync(new ConnectTask(false), (result) -> {
            mConnected = result;
            invokeDeviceUpdated(this);
        });
    }

    public void addListener(BLEDeviceChangedListener listener) {
        if(!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(BLEDeviceChangedListener listener) {
        try {
            listeners.remove(listener);
        } catch (Exception ignored) { }
    }

    private void invokeDeviceUpdated(BLEDevice device) {
        for (BLEDeviceChangedListener listener: listeners) {
            listener.deviceUpdated(device);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BLEDevice bleDevice = (BLEDevice) o;
        return Objects.equals(mName, bleDevice.mName) && Objects.equals(mAddress, bleDevice.mAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mAddress);
    }
}
