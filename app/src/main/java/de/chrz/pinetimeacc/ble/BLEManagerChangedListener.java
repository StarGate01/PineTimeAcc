package de.chrz.pinetimeacc.ble;

import de.chrz.pinetimeacc.sampling.Sample;

public interface BLEManagerChangedListener {
    void deviceListUpdated();
    void individualDeviceUpdated(BLEDevice device);
    void individualDataIncoming(Sample[] data);
}
