package de.chrz.pinetimeacc;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public class BLEDevice extends BluetoothGattCallback implements Comparable<BLEDevice> {
    private final String name;
    private final String address;
    private final BluetoothDevice device;
    private final Context context;
    private BluetoothGatt gatt;

    private final List<BLEDeviceChangedListener> listeners;
    private final UUID motionUuidChar = UUID.fromString("00030002-78fc-48fe-8e23-433b3a1942d0");
    private final UUID motionUuidService = UUID.fromString("00030000-78fc-48fe-8e23-433b3a1942d0");
    private final UUID clientConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    @Override
    public int compareTo(BLEDevice bleDevice) {
        return name.compareTo(bleDevice.getName());
    }

    public BLEDevice(String name, String address, BluetoothDevice device, Context context) {
        this.name = name;
        this.address = address;
        this.device = device;
        this.context = context;

        listeners = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public boolean isConnected() {
        return gatt != null;
    }

    public void connect() {
        try {
            device.connectGatt(context, false, this);
        } catch (SecurityException ignored) { }
    }

    public void disconnect() {
        if(gatt != null) {
            try {
                gatt.disconnect();
            } catch (SecurityException ignored) { }
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            try {
                gatt.discoverServices();
                return;
            } catch (SecurityException ignored) { }
        }

        try {
            if (gatt != null) gatt.close();
        } catch (SecurityException ignored) { }
        this.gatt = null;
        invokeDeviceUpdated();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        boolean success = false;

        try {
            BluetoothGattService motionService = gatt.getService(motionUuidService);
            if(motionService != null) {
                BluetoothGattCharacteristic motionChar = motionService.getCharacteristic(motionUuidChar);
                if(motionChar != null) {
                    gatt.setCharacteristicNotification(motionChar, true);
                    BluetoothGattDescriptor descriptor = motionChar.getDescriptor(clientConfigUuid);
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                    success = true;
                }
            }
        } catch (SecurityException ignored) { }

        if(success) {
            this.gatt = gatt;
        } else {
            try {
                gatt.disconnect();
            } catch (SecurityException ignored) { }
        }

        invokeDeviceUpdated();
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if(characteristic.getUuid().equals(motionUuidChar)) {
            double x = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0) / 1000.0;
            double y = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 2) / 1000.0;
            double z = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 4) / 1000.0;
            double mag = Math.sqrt((x*x) + (y*y) + (z*z));
            invokeDataIncoming(new double[] { x, y, z, mag });
        }
    }

    public void addListener(BLEDeviceChangedListener listener) {
        if(!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(BLEDeviceChangedListener listener) {
        try {
            listeners.remove(listener);
        } catch (Exception ignored) { }
    }

    private void invokeDeviceUpdated() {
        for (BLEDeviceChangedListener listener: listeners) {
            listener.deviceUpdated(this);
        }
    }

    private void invokeDataIncoming(double[] data) {
        for (BLEDeviceChangedListener listener: listeners) {
            listener.dataIncoming(data);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BLEDevice bleDevice = (BLEDevice) o;
        return Objects.equals(name, bleDevice.name) && Objects.equals(address, bleDevice.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, address);
    }
}
