package de.chrz.pinetimeacc.ui.home;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import de.chrz.pinetimeacc.R;

public class AboutFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_about, container, false);

        TextView gh = v.findViewById(R.id.textView_github);
        gh.setMovementMethod(LinkMovementMethod.getInstance());

        TextView gh_graph = v.findViewById(R.id.textView_github2);
        gh_graph.setMovementMethod(LinkMovementMethod.getInstance());

        return v;
    }

}