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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import de.chrz.pinetimeacc.BLEDevice;
import de.chrz.pinetimeacc.BLEManagerChangedListener;
import de.chrz.pinetimeacc.MainActivity;
import de.chrz.pinetimeacc.R;
import de.chrz.pinetimeacc.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements BLEManagerChangedListener {

    private List<LineGraphSeries<DataPoint>> series;
    private LinkedList<double[]> seriesData;
    private final int filterSize = 10;
    private int seriesTime = 2000;
    private long seriesCount = 0;

    private HomeViewModel homeViewModel;
    private GraphView graph, graph_norm;

    private Handler mainHandler;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        FragmentHomeBinding binding = FragmentHomeBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        mainHandler = new Handler(Looper.getMainLooper());

        final TextView textTitle = binding.textActivetitle;
        homeViewModel.getTitle().observe(getViewLifecycleOwner(), textTitle::setText);
        final TextView textDelta = binding.textDeltatime;
        homeViewModel.getDelta().observe(getViewLifecycleOwner(), textDelta::setText);
        final TextView textFreq = binding.textFreq;
        homeViewModel.getFreq().observe(getViewLifecycleOwner(), textFreq::setText);

        graph = v.findViewById(R.id.graph);
        graph_norm = v.findViewById(R.id.graph_norm);
        series = new ArrayList<>();
        for(int i=0; i<3; i++) {
            LineGraphSeries<DataPoint> s = new LineGraphSeries<>();
            series.add(s);
            graph.addSeries(s);
        }
        series.get(0).setColor(Color.RED);
        series.get(0).setTitle("X");
        series.get(1).setColor(Color.rgb(0, 200, 70));
        series.get(1).setTitle("Y");
        series.get(2).setColor(Color.YELLOW);
        series.get(2).setTitle("Z");

        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxY(2.0);
        graph.getViewport().setMinY(-2.0);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMaxX(seriesTime);
        graph.getViewport().setMinX(0);

        LineGraphSeries<DataPoint> sn = new LineGraphSeries<>();
        series.add(sn);
        graph_norm.addSeries(sn);
        sn.setColor(Color.rgb(0, 175, 255));

        graph_norm.getViewport().setYAxisBoundsManual(true);
        graph_norm.getViewport().setMaxY(2.0);
        graph_norm.getViewport().setMinY(0.0);
        graph_norm.getViewport().setXAxisBoundsManual(true);
        graph_norm.getViewport().setMaxX(seriesTime);
        graph_norm.getViewport().setMinX(0);

        EditText timeWindow = v.findViewById(R.id.edit_timewindow);
        timeWindow.setText(String.format(Locale.getDefault(), "%d", seriesTime));
        timeWindow.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence,
                                          int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start,
                                      int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    seriesTime = Math.max(10, c);
                } catch (NumberFormatException e) {
                    seriesTime = 10;
                }

                graph.getViewport().setMinX(seriesCount - seriesTime);
                graph.getViewport().setMaxX(seriesCount);
                graph_norm.getViewport().setMinX(seriesCount - seriesTime);
                graph_norm.getViewport().setMaxX(seriesCount);
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        MainActivity main = (MainActivity)getActivity();
        if(main != null) {
            MainActivity.bleManager.addListener(this);
        }

        updateTitle();

        seriesData = new LinkedList<>();

        return v;
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
        // Defer to main thread
        Runnable updateGraph = () -> {
            for (double[] datum : data) {
                double[] filtered = new double[4];
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

                seriesData.add(datum);
                while(seriesData.size() > seriesTime) seriesData.poll();

                int i = 0;
                for (LineGraphSeries<DataPoint> s : series) {
                    if(i < 3) {
                        s.appendData(new DataPoint(seriesCount, filtered[i]), true, seriesTime);
                    } else {
                        double norm = Math.sqrt((filtered[0] * filtered[0]) +
                                (filtered[1] * filtered[1]) + (filtered[2] * filtered[2]));
                        s.appendData(new DataPoint(seriesCount, norm), true, seriesTime);
                    }
                    i++;
                }
                seriesCount++;
            }
            graph.getViewport().setMinX(seriesCount - seriesTime);
            graph.getViewport().setMaxX(seriesCount);
            graph_norm.getViewport().setMinX(seriesCount - seriesTime);
            graph_norm.getViewport().setMaxX(seriesCount);
        };
        mainHandler.post(updateGraph);
    }

}