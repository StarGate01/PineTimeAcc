package de.chrz.pinetimeacc.ui.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import de.chrz.pinetimeacc.BLEDevice;
import de.chrz.pinetimeacc.BLEManager;
import de.chrz.pinetimeacc.BLEManagerChangedListener;
import de.chrz.pinetimeacc.R;
import de.chrz.pinetimeacc.BLEDeviceChangedListener;

public class BLEDeviceAdapter extends
    RecyclerView.Adapter<BLEDeviceAdapter.ViewHolder> implements BLEDeviceChangedListener, BLEManagerChangedListener {

    private final BLEManager manager;
    BLEDevice activeDevice = null;

    public BLEDeviceAdapter(BLEManager manager) {
        this.manager = manager;
        this.manager.addListener(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View contactView = inflater.inflate(R.layout.item_bledevice, parent, false);
        return new ViewHolder(contactView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BLEDevice device = manager.getDevices().get(position);

        TextView nameTextView = holder.nameTextView;
        nameTextView.setText(device.getName());
        TextView addressTextView = holder.addressTextView;
        addressTextView.setText(device.getAddress());
        Button connectButton = holder.connectButton;
        connectButton.setText(device.isConnected() ? "Disconnect" : "Connect");

        if(activeDevice != null) {
            connectButton.setEnabled(device == activeDevice);
        } else {
            connectButton.setEnabled(true);
        }

        connectButton.setOnClickListener(view -> {
            if(!device.isConnected()) {
                device.connect();
            } else {
                device.disconnect();
            }
        });

        device.addListener(this);
    }

    @Override
    public int getItemCount() {
        activeDevice = manager.getActiveDevice();
        return manager.getDevices().size();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void deviceUpdated(BLEDevice device) {
        activeDevice = manager.getActiveDevice();
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void deviceListUpdated() {
        notifyDataSetChanged();
    }

    @Override
    public void individualDeviceUpdated(BLEDevice device) {
        // Already handled in deviceUpdated
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView nameTextView;
        public TextView addressTextView;
        public Button connectButton;

        public ViewHolder(View itemView) {
            super(itemView);

            nameTextView = itemView.findViewById(R.id.device_name);
            addressTextView = itemView.findViewById(R.id.device_address);
            connectButton = itemView.findViewById(R.id.connect_button);
        }
    }
}
