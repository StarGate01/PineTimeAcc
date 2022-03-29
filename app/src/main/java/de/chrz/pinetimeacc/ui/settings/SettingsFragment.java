package de.chrz.pinetimeacc.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.chrz.pinetimeacc.MainActivity;
import de.chrz.pinetimeacc.R;

public class SettingsFragment extends Fragment {

    private BLEDeviceAdapter adapter = null;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        RecyclerView rvDevices = v.findViewById(R.id.rv_devices);

        MainActivity main = (MainActivity)getActivity();
        if(main != null) {
            if (adapter == null)
                adapter = new BLEDeviceAdapter(main.bleManager, this.getActivity());
            rvDevices.setAdapter(adapter);
            rvDevices.setLayoutManager(new LinearLayoutManager(this.getContext()));
            main.bleManager.beginScan();
        }

        return v;
    }

}