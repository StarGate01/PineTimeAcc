package de.chrz.pinetimeacc;

public interface BLEManagerChangedListener {
    void deviceListUpdated();
    void individualDeviceUpdated(BLEDevice device);
    void individualDataIncoming(double[] data);
}
