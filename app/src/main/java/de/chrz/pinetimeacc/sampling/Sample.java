package de.chrz.pinetimeacc.sampling;

import java.util.Iterator;
import java.util.LinkedList;

public class Sample {

    public final Vector3 raw = new Vector3();
    public final Vector3 smoothed = new Vector3();

    public double mag, filteredMag, avgFilteredMag, stdFilteredMag;
    public int peak;

    public void smooth(LinkedList<Sample> oldSamples, int filterSize) {
        Iterator<Sample> iterate = oldSamples.descendingIterator();
        for(int i=0; i<filterSize; i++) smoothed.add(iterate.next().raw);
        smoothed.multiply(1.0 / (double)filterSize);
    }

}
