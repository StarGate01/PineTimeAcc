package de.chrz.pinetimeacc.ble;

import de.chrz.pinetimeacc.sampling.Sample;

public interface BLEDeviceChangedListener {
    void deviceUpdated(BLEDevice device);
    void dataIncoming(Sample[] data);
}
