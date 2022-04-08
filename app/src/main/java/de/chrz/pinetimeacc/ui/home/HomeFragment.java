package de.chrz.pinetimeacc.ui.home;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import de.chrz.pinetimeacc.MainActivity;
import de.chrz.pinetimeacc.R;
import de.chrz.pinetimeacc.ble.BLEDevice;
import de.chrz.pinetimeacc.ble.BLEManagerChangedListener;
import de.chrz.pinetimeacc.databinding.FragmentHomeBinding;
import de.chrz.pinetimeacc.sampling.PeakDetector;
import de.chrz.pinetimeacc.sampling.Sample;

public class HomeFragment extends Fragment implements BLEManagerChangedListener {

    private final ConcurrentLinkedDeque<Sample> seriesData = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Sample> jitterData = new ConcurrentLinkedDeque<>();

    // Signal configuration
    private int filterSize = 4; // samples
    private int timeWindow = 5000; // milliseconds
    private int jitterSize = 50; // samples
    private int sampleRate = 200; // Hertz

    // Signal status
    private final AtomicInteger jitterDropped = new AtomicInteger(); // samples
    private long seriesCount = 0; // samples
    private final PeakDetector peakDetector = new PeakDetector();
    private boolean triggerRising = true;
    private boolean triggerFalling = false;

    // GUI performance
    private int guiMaxPoints = 200; // count
    private int guiDownsample; // count
    private int guiUpdateFreq = 25; // ms
    private final int guiLabelUpdateFreq = 250; // ms

    private HomeViewModel homeViewModel;
    private GraphView graph;
    private LineGraphSeries<DataPoint> seriesMag, seriesStdFilteredMag, seriesStdFilteredMag2, seriesPeaks;
    private Handler mainHandler;
    private Runnable refreshTimer, refreshLabelTimer;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        mainHandler = new Handler(Looper.getMainLooper());

        FragmentHomeBinding binding = FragmentHomeBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        setupLabels(binding);
        setupGraph(v);
        setupInput(v);

        MainActivity main = (MainActivity)getActivity();
        if(main != null)  MainActivity.bleManager.addListener(this);

        updateTitle();
        updateUIDownsample();

        return v;
    }

    private void setupLabels(FragmentHomeBinding binding) {
        final TextView textTitle = binding.textActivetitle;
        homeViewModel.getTitle().observe(getViewLifecycleOwner(), textTitle::setText);
        final TextView textDelta = binding.textDeltatime;
        homeViewModel.getDelta().observe(getViewLifecycleOwner(), textDelta::setText);
        final TextView textFreq = binding.textFreq;
        homeViewModel.getFreq().observe(getViewLifecycleOwner(), textFreq::setText);
        final TextView textJitter = binding.textJitter;
        homeViewModel.getJitter().observe(getViewLifecycleOwner(), textJitter::setText);
        final TextView textUiDownsample = binding.textUidownsample;
        homeViewModel.getUIDownsample().observe(getViewLifecycleOwner(), textUiDownsample::setText);
    }

    private void setupGraph(View v) {
        graph = v.findViewById(R.id.graph);
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxY(3.0);
        graph.getViewport().setMinY(-1.0);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMaxX(timeWindow);
        graph.getViewport().setMinX(0);
        int chartLineWidth = 2; // pixel

        seriesMag = new LineGraphSeries<>();
        seriesMag.setColor(Color.rgb(0, 175, 255));
        seriesMag.setTitle("Mag");
        seriesMag.setThickness(chartLineWidth);
        graph.addSeries(seriesMag);

        seriesStdFilteredMag = new LineGraphSeries<>();
        seriesStdFilteredMag.setColor(Color.YELLOW);
        seriesStdFilteredMag.setTitle("High");
        seriesStdFilteredMag.setThickness(chartLineWidth);
        graph.addSeries(seriesStdFilteredMag);

        seriesStdFilteredMag2 = new LineGraphSeries<>();
        seriesStdFilteredMag2.setColor(Color.YELLOW);
        seriesStdFilteredMag2.setTitle("Low");
        seriesStdFilteredMag2.setThickness(chartLineWidth);
        graph.addSeries(seriesStdFilteredMag2);

        seriesPeaks = new LineGraphSeries<>();
        seriesPeaks.setColor(Color.RED);
        seriesPeaks.setTitle("Peak");
        seriesPeaks.setThickness(chartLineWidth);
        graph.addSeries(seriesPeaks);
    }

    private void setupInput(View v) {
        EditText editTimeWindow = v.findViewById(R.id.edit_timewindow);
        editTimeWindow.setText(String.format(Locale.getDefault(), "%d", timeWindow));
        editTimeWindow.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    timeWindow = Math.max(10, c);
                } catch (NumberFormatException e) {
                    timeWindow = 10;
                }
                updateGraphScale();
                updateUIDownsample();
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editFilterSize = v.findViewById(R.id.edit_filtersize);
        editFilterSize.setText(String.format(Locale.getDefault(), "%d", filterSize));
        editFilterSize.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    filterSize = Math.max(1, c);
                } catch (NumberFormatException e) {
                    filterSize = 8;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editJitterSize = v.findViewById(R.id.edit_jitter);
        editJitterSize.setText(String.format(Locale.getDefault(), "%d", jitterSize));
        editJitterSize.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    jitterSize = Math.max(1, c);
                } catch (NumberFormatException e) {
                    jitterSize = 30;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editSampleRate = v.findViewById(R.id.edit_samplerate);
        editSampleRate.setText(String.format(Locale.getDefault(), "%d", sampleRate));
        editSampleRate.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    sampleRate = Math.max(1, c);
                } catch (NumberFormatException e) {
                    sampleRate = 200;
                }
                updateUIDownsample();
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editMaxPoints = v.findViewById(R.id.edit_maxpoints);
        editMaxPoints.setText(String.format(Locale.getDefault(), "%d", guiMaxPoints));
        editMaxPoints.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    guiMaxPoints = Math.max(10, c);
                } catch (NumberFormatException e) {
                    guiMaxPoints = 300;
                }
                updateUIDownsample();
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editRefreshRate = v.findViewById(R.id.edit_refreshrate);
        editRefreshRate.setText(String.format(Locale.getDefault(), "%d", guiUpdateFreq));
        editRefreshRate.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    guiUpdateFreq = Math.max(1, c);
                } catch (NumberFormatException e) {
                    guiUpdateFreq = 30;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editDetLag = v.findViewById(R.id.edit_detlag);
        editDetLag.setText(String.format(Locale.getDefault(), "%d", peakDetector.lag));
        editDetLag.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    peakDetector.lag = Math.max(1, c);
                } catch (NumberFormatException e) {
                    peakDetector.lag = 8;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editDetThreshold = v.findViewById(R.id.edit_detthreshold);
        editDetThreshold.setText(String.format(Locale.getDefault(), "%f", peakDetector.threshold));
        editDetThreshold.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
                    Number number = format.parse(charSequence.toString());
                    if(number != null) {
                        double c = number.doubleValue();
                        peakDetector.threshold = Math.max(1.0, c);
                    }
                } catch (ParseException e) {
                    peakDetector.threshold = 3.5;
                }
                updateUIDownsample();
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editDetInfluence = v.findViewById(R.id.edit_detinfluence);
        editDetInfluence.setText(String.format(Locale.getDefault(), "%d", (int)(peakDetector.influence * 100)));
        editDetInfluence.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    peakDetector.influence = (double)Math.max(0, c) / 100.0;
                } catch (NumberFormatException e) {
                    peakDetector.influence = 0.5;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        SwitchCompat switchRising = v.findViewById(R.id.switch_rising);
        switchRising.setChecked(triggerRising);
        switchRising.setOnCheckedChangeListener((compoundButton, b) -> triggerRising = b);

        SwitchCompat switchFalling = v.findViewById(R.id.switch_falling);
        switchFalling.setChecked(triggerFalling);
        switchFalling.setOnCheckedChangeListener((compoundButton, b) -> triggerFalling = b);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshTimer = new Runnable() {
            @Override
            public void run() {
                processJitter();
                mainHandler.postDelayed(this, guiUpdateFreq);
            }
        };
        refreshLabelTimer = new Runnable() {
            @Override
            public void run() {
                updateLabels();
                mainHandler.postDelayed(this, guiLabelUpdateFreq);
            }
        };
        mainHandler.postDelayed(refreshTimer, guiUpdateFreq);
        mainHandler.postDelayed(refreshLabelTimer, guiLabelUpdateFreq);
    }

    @Override
    public void onPause() {
        mainHandler.removeCallbacks(refreshTimer);
        mainHandler.removeCallbacks(refreshLabelTimer);
        super.onPause();
    }

    private void updateTitle() {
        MainActivity main = (MainActivity)getActivity();
        if(main != null) {
            BLEDevice activeDevice = MainActivity.bleManager.getActiveDevice();
            if (activeDevice != null) {
                homeViewModel.getTitle().postValue(activeDevice.getName());
            } else {
                homeViewModel.getTitle().postValue(getResources().getString(R.string.no_device_connected));
            }
        }
    }

    private void updateGraphScale() {
        graph.getViewport().setMinX((double)seriesCount / (double)sampleRate - ((double)timeWindow / 1000.0));
        graph.getViewport().setMaxX((double)seriesCount / (double)sampleRate);
    }

    private void updateUIDownsample() {
        guiDownsample = Math.max(((timeWindow / 1000) * sampleRate) / guiMaxPoints, 1);
        homeViewModel.getUIDownsample().postValue(Integer.toString(guiDownsample));
    }

    private void processJitter() {
        int windowSize = (timeWindow * sampleRate) / 1000;
        int numFrames = (int)Math.ceil((double)sampleRate / (double) guiUpdateFreq);
        for(int k=0; k<numFrames; k++) {
            double sampleTime = (double)seriesCount / (double)sampleRate;
            Sample sample = jitterData.poll();
            if((sample != null) && (seriesCount % guiDownsample == 0)) {
                seriesMag.appendData(new DataPoint(sampleTime, sample.mag), true, windowSize);
                seriesStdFilteredMag.appendData(new DataPoint(sampleTime,
                        sample.avgFilteredMag + (peakDetector.threshold * sample.stdFilteredMag)), true, windowSize);
                seriesStdFilteredMag2.appendData(new DataPoint(sampleTime,
                        sample.avgFilteredMag - (peakDetector.threshold * sample.stdFilteredMag)), true, windowSize);
                seriesPeaks.appendData(new DataPoint(sampleTime, sample.peak), true, windowSize);
            }
            if(sample != null) seriesCount++;
            else break;
        }
        updateGraphScale();
    }

    private void updateLabels() {
        homeViewModel.getJitter().postValue(String.format(Locale.getDefault(),
                "%d / %d", jitterDropped.get(), jitterData.size()));

        // Measure distances between triggers
        ArrayList<Integer> distances = new ArrayList<>();
        int lastTriggerSample = -1;
        boolean lastState = false;
        int sampleCount = 0;
        for (Sample sample: seriesData) {
            if(!lastState &&
                    ((triggerRising && sample.peak == +1) ||
                    (triggerFalling && sample.peak == -1))) {
                lastState = true;
                if(lastTriggerSample != -1) {
                    distances.add(sampleCount - lastTriggerSample);
                }
                lastTriggerSample = sampleCount;
            } else {
                lastState = false;
            }
            sampleCount++;
        }

        // Find size of latest distance
        String delta = "N/A";
        if(distances.size() > 0) delta = String.format(Locale.getDefault(), "%d", distances.get(distances.size() - 1));
        homeViewModel.getDelta().postValue(delta);

        // Compute average frequency in bpm
        String freq = "N/A";
        if(distances.size() > 0) {
            double frequency = 0;
            for (int distance : distances) frequency += distance;
            frequency /= (double) distances.size();
            frequency = 60.0 / (frequency * (1.0 / (double)sampleRate));
            freq = String.format(Locale.getDefault(), "%.2f", frequency);
        }
        homeViewModel.getFreq().postValue(freq);
    }

    @Override
    public void deviceListUpdated() {
        updateTitle();
    }

    @Override
    public void individualDeviceUpdated(BLEDevice device) {
        updateTitle();
    }

    @Override
    public void individualDataIncoming(Sample[] data) {
        for (Sample datum : data) {
            // Pre-smooth signal
            if(seriesData.size() >= filterSize) {
                datum.smooth(seriesData, filterSize);
                datum.mag = datum.smoothed.magnitude();
            } else {
                datum.mag = datum.raw.magnitude();
            }

            // Find peaks
            if(seriesData.size() >= peakDetector.lag) {
                peakDetector.process(seriesData, datum);
            } else {
                datum.filteredMag = datum.mag;
                if(seriesData.size() > 0) {
                    PeakDetector.computeAvgStd(datum, 0, 0, seriesData, seriesData.size());
                } else {
                    PeakDetector.computeAvgStd(datum, datum.filteredMag, datum.filteredMag * datum.filteredMag, null, 1);
                }
            }

            // Update buffers
            seriesData.add(datum);
            while(seriesData.size() > timeWindow) seriesData.poll();
            if(jitterData.size() < jitterSize) jitterData.add(datum);
            else jitterDropped.incrementAndGet();
        }
    }

}