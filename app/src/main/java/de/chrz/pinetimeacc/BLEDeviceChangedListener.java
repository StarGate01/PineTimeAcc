package de.chrz.pinetimeacc;

public interface BLEDeviceChangedListener {
    void deviceUpdated(BLEDevice device);
    void dataIncoming(double[] data);
}
