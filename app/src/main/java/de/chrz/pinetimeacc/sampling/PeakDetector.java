package de.chrz.pinetimeacc.sampling;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

// Based on https://stackoverflow.com/questions/22583391/peak-signal-detection-in-realtime-timeseries-data/22640362#22640362

public class PeakDetector {

    public int lag = 16; // samples
    public double threshold = 2.0; // std. deviations
    public double influence = 0.25; // percent

    public void process(ConcurrentLinkedDeque<Sample> oldSamples, Sample newSample) {
        Sample prev = oldSamples.getLast();

        // Check threshold
        if(Math.abs(newSample.mag - prev.avgFilteredMag) > (threshold * prev.stdFilteredMag)) {
            if(newSample.mag > prev.avgFilteredMag) newSample.peak = +1;
            else newSample.peak = -1;
            newSample.filteredMag = (influence * newSample.mag) + ((1.0 - influence) * prev.filteredMag);
        } else {
            newSample.filteredMag = newSample.mag;
        }

        // Compute average and mean, use already iterated element as initialisation
        computeAvgStd(newSample, prev.filteredMag, prev.filteredMag * prev.filteredMag, oldSamples, lag);
    }

    public static void computeAvgStd(Sample target, double sumMag, double sumMagSquare, ConcurrentLinkedDeque<Sample> set, int setSize) {
        if(set != null) {
            Iterator<Sample> iterate = set.descendingIterator();
            for (int i = 0; i < setSize; i++) {
                Sample sample = iterate.next();
                sumMag += sample.filteredMag;
            }
        }
        target.avgFilteredMag = sumMag / (double) setSize;

        if(set != null) {
            Iterator<Sample> iterate = set.descendingIterator();
            for (int i = 0; i < setSize; i++) {
                Sample sample = iterate.next();
                sumMagSquare += (sample.filteredMag - target.avgFilteredMag) * (sample.filteredMag - target.avgFilteredMag);
            }
        }
        target.stdFilteredMag = Math.sqrt(sumMagSquare / ((double)setSize - 1.0));
    }

}
