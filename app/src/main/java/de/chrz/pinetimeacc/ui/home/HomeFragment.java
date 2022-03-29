package de.chrz.pinetimeacc.ui.home;

import android.os.Bundle;
import android.os.Handler;
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
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Locale;
import java.util.Random;

import de.chrz.pinetimeacc.BLEDevice;
import de.chrz.pinetimeacc.BLEManagerChangedListener;
import de.chrz.pinetimeacc.MainActivity;
import de.chrz.pinetimeacc.R;
import de.chrz.pinetimeacc.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment implements BLEManagerChangedListener {

    private final Handler handler = new Handler();
    private Runnable updateTimer;
    private LineGraphSeries<DataPoint> seriesX;
    Random rand = new Random();
    int seriesCount = 30;

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View v = binding.getRoot();

        final TextView textTitle = binding.textActivetitle;
        homeViewModel.getTitle().observe(getViewLifecycleOwner(), textTitle::setText);
        final TextView textDelta = binding.textDeltatime;
        homeViewModel.getDelta().observe(getViewLifecycleOwner(), textDelta::setText);
        final TextView textFreq = binding.textFreq;
        homeViewModel.getFreq().observe(getViewLifecycleOwner(), textFreq::setText);

        GraphView graph = v.findViewById(R.id.graph);
        seriesX = new LineGraphSeries<>(generateData());
        graph.addSeries(seriesX);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(seriesCount);

        EditText timeWindow = v.findViewById(R.id.edit_timewindow);
        timeWindow.setText(String.format(Locale.getDefault(), "%d", seriesCount));
        timeWindow.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence,
                                          int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int start,
                                      int before, int count) {
                try {
                    int c = Integer.parseInt(charSequence.toString());
                    seriesCount = Math.max(10, c);
                } catch (NumberFormatException e) {
                    seriesCount = 10;
                }
                graph.getViewport().setMaxX(seriesCount);
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        MainActivity.bleManager.addListener(this);

        updateTitle();

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTimer = new Runnable() {
            @Override
            public void run() {
                seriesX.resetData(generateData());
                handler.postDelayed(this, 300);
            }
        };
        handler.postDelayed(updateTimer, 300);
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(updateTimer);
        super.onPause();
    }

    private DataPoint[] generateData() {
        DataPoint[] values = new DataPoint[seriesCount];
        for (int i=0; i<seriesCount; i++) {
            double x = i;
            double f = rand.nextDouble()*0.15+0.3;
            double y = Math.sin(i*f+2) + rand.nextDouble()*0.3;
            DataPoint v = new DataPoint(x, y);
            values[i] = v;
        }
        return values;
    }

    private void updateTitle() {
        BLEDevice activeDevice = MainActivity.bleManager.getActiveDevice();
        if(activeDevice != null) {
            homeViewModel.getTitle().setValue(activeDevice.getName());
        } else {
            homeViewModel.getTitle().setValue(getResources().getString(R.string.no_device_connected));
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

}