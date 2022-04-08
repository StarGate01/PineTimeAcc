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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import de.chrz.pinetimeacc.BLEDevice;
import de.chrz.pinetimeacc.BLEManagerChangedListener;
import de.chrz.pinetimeacc.MainActivity;
import de.chrz.pinetimeacc.R;
import de.chrz.pinetimeacc.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements BLEManagerChangedListener {

    private final LinkedList<double[]> seriesData = new LinkedList<>(); // xyz data
    private final ConcurrentLinkedQueue<double[]> jitterData = new ConcurrentLinkedQueue<>(); // normalized data

    // Signal configuration
    private int filterSize = 4; // samples
    private int timeWindow = 5000; // milliseconds
    private int jitterSize = 30; // samples
    private int sampleRate = 200; // Hertz

    // Signal status
    private long jitterDropped = 0; // samples
    private long seriesCount = 0; // samples

    // Detection configuration
    private int detFilterSize = 8; // samples
    private double detThreshold = 3.5; // std. deviations
    private int detInfluence = 50; // percent

    // GUI performance
    private int guiMaxPoints = 250; // count
    private int guiDownsample; // count
    private int guiUpdateFreq = 40; // Hertz

    private HomeViewModel homeViewModel;
    private GraphView graph;
    private final LineGraphSeries<DataPoint> seriesMag = new LineGraphSeries<>();
    private Handler mainHandler;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        mainHandler = new Handler(Looper.getMainLooper());

        FragmentHomeBinding binding = FragmentHomeBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        setupLabels(binding);
        setupGraph(v);
        setupEditText(v);

        MainActivity main = (MainActivity)getActivity();
        if(main != null)  MainActivity.bleManager.addListener(this);

        startTimer();
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
        graph.getViewport().setMaxY(2.0);
        graph.getViewport().setMinY(0.0);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMaxX(timeWindow);
        graph.getViewport().setMinX(0);

        seriesMag.setColor(Color.rgb(0, 175, 255));
        seriesMag.setTitle("Mag");
        graph.addSeries(seriesMag);
    }

    private void setupEditText(View v) {
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

        EditText editDetFilterSize = v.findViewById(R.id.edit_detavgsize);
        editDetFilterSize.setText(String.format(Locale.getDefault(), "%d", detFilterSize));
        editDetFilterSize.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    detFilterSize = Math.max(1, c);
                } catch (NumberFormatException e) {
                    detFilterSize = 8;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editDetThreshold = v.findViewById(R.id.edit_detthreshold);
        editDetThreshold.setText(String.format(Locale.getDefault(), "%f", detThreshold));
        editDetThreshold.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    double c = Double.parseDouble(charSequence.toString());
                    detThreshold = Math.max(1.0, c);
                } catch (NumberFormatException e) {
                    detThreshold = 3.5;
                }
                updateUIDownsample();
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        EditText editDetInfluence = v.findViewById(R.id.edit_detinfluence);
        editDetInfluence.setText(String.format(Locale.getDefault(), "%d", detInfluence));
        editDetInfluence.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    detInfluence = Math.max(0, c);
                } catch (NumberFormatException e) {
                    detInfluence = 50;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) { }

            @Override
            public void afterTextChanged(Editable editable) { }
        });
    }

    private void startTimer() {
        Runnable refreshTimer = new Runnable() {
            @Override
            public void run() {
                processJitter();
                mainHandler.postDelayed(this, guiUpdateFreq);
            }
        };
        mainHandler.postDelayed(refreshTimer, guiUpdateFreq);
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
            double[] filtered = jitterData.poll();
            if((filtered != null) && (seriesCount % guiDownsample == 0)) {
                seriesMag.appendData(new DataPoint(sampleTime, filtered[0]), true, windowSize);
            }
            if(filtered != null) seriesCount++;
            else break;
        }
        updateGraphScale();
        homeViewModel.getJitter().postValue(String.format(Locale.getDefault(),
                "%d / %d", jitterDropped, jitterData.size()));
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
    public void individualDataIncoming(double[][] data) {
        for (double[] datum : data) {
            // Step 1, pre-filter using sliding window
            double[] filtered = new double[3];
            if(seriesData.size() >= filterSize) {
                Iterator<double[]> iterator = seriesData.descendingIterator();
                for(int i=0; i<filterSize; i++) {
                    double[] part = iterator.next();
                    for(int j=0; j<3; j++) filtered[j] += part[j];
                }
                for(int j=0; j<3; j++) filtered[j] /= filterSize;
            } else {
                filtered = datum;
            }

            // Step 2, compute magnitude (input signal)
            double mag = Math.sqrt((filtered[0] * filtered[0]) +
                    (filtered[1] * filtered[1]) + (filtered[2] * filtered[2]));

//            // Step 3, detect peaks using z-scores
//            double influence = (double)detInfluence / 100.0;
//            int isPeak = 0;
//            if(seriesData.size() >= detFilterSize) {
//
//                // Step 3.1, compute signal sliding average
//                Iterator<double[]> iterator = seriesData.listIterator(seriesData.size() - detFilterSize);
//                for(int i=)
//                double mag_avg = 0.0;
//                double mag_avg_prev = 0.0;
//                for(int i=0; i<detFilterSize; i++) {
//                    double[] part = iterator.next();
//                    if(i == 0) mag_avg_prev = part[4];
//                    mag_avg += part[3];
//                }
//                mag_avg /= detFilterSize;
//
//                // Step 3.2, compare to average
//                if(Math.abs(mag - mag_avg) > detThreshold * 1) { // todo stdFilter
//                    isPeak = +1;
//                } else {
//                    isPeak = -1;
//                }
//
//                // Step 3.3, store adjusted signal
//                double mag_adj = ((influence * mag) + (1.0 - influence) *
//            }


            // Add to historical data
            double[] hist = new double[] { datum[0], datum[1], datum[2], mag };
            seriesData.add(hist);
            while(seriesData.size() > timeWindow) seriesData.poll();

            // Add to jitter buffer
            if(jitterData.size() < jitterSize) jitterData.add(new double[] { mag });
            else jitterDropped++;
        }
    }

}