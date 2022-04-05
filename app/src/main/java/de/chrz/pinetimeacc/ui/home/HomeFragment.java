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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.chrz.pinetimeacc.BLEDevice;
import de.chrz.pinetimeacc.BLEManagerChangedListener;
import de.chrz.pinetimeacc.MainActivity;
import de.chrz.pinetimeacc.R;
import de.chrz.pinetimeacc.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements BLEManagerChangedListener {

    private List<LineGraphSeries<DataPoint>> series;
    private int seriesTime = 2000;
    private int seriesCount = 0;

    private HomeViewModel homeViewModel;
    private GraphView graph;

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
        series = new ArrayList<>();
        for(int i=0; i<3; i++) {
            LineGraphSeries<DataPoint> s = new LineGraphSeries<>();
            series.add(s);
            graph.addSeries(s);
        }
        series.get(0).setColor(Color.RED);
        series.get(0).setTitle("X");
        series.get(1).setColor(Color.GREEN);
        series.get(1).setTitle("Y");
        series.get(2).setColor(Color.YELLOW);
        series.get(2).setTitle("Z");

        LineGraphSeries<DataPoint> sn = new LineGraphSeries<>();
        series.add(sn);
        graph.getSecondScale().addSeries(sn);
        graph.getSecondScale().setMinY(0);
        graph.getSecondScale().setMaxY(2);
        sn.setColor(Color.BLUE);
        sn.setTitle("Mag");
        graph.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.BLUE);

        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxY(1.2);
        graph.getViewport().setMinY(-1.2);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMaxX(seriesTime);
        graph.getViewport().setMinX(0);

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

                graph.getViewport().setMinX(0);
                graph.getViewport().setMaxX(seriesTime);
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        SwitchCompat switchXYZ = v.findViewById(R.id.switch_xyzdata);
        switchXYZ.setOnCheckedChangeListener((compoundButton, b) -> {

        });
        SwitchCompat switchMag = v.findViewById(R.id.switch_magnitude);
        switchMag.setOnCheckedChangeListener((compoundButton, b) -> {

        });

        MainActivity main = (MainActivity)getActivity();
        if(main != null) {
            main.bleManager.addListener(this);
        }

        updateTitle();

        return v;
    }

    private void updateTitle() {
        MainActivity main = (MainActivity)getActivity();
        if(main != null) {
            BLEDevice activeDevice = main.bleManager.getActiveDevice();
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
                int i = 0;
                if (seriesCount >= seriesTime) {
                    for (LineGraphSeries<DataPoint> s : series) {
                        s.resetData(new DataPoint[]{new DataPoint(0, datum[i])});
                        i++;
                    }
                    seriesCount = 1;
                } else {
                    for (LineGraphSeries<DataPoint> s : series) {
                        s.appendData(new DataPoint(seriesCount, datum[i]), true, seriesCount + 1);
                        i++;
                    }
                    seriesCount++;
                }
            }
            graph.getViewport().setMinX(0);
            graph.getViewport().setMaxX(seriesTime);
        };
        mainHandler.post(updateGraph);
    }

}