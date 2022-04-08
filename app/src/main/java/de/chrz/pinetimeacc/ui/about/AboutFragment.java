package de.chrz.pinetimeacc.ui.about;

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

        TextView url_icon = v.findViewById(R.id.textView_icon1);
        url_icon.setMovementMethod(LinkMovementMethod.getInstance());

        TextView url_icon2 = v.findViewById(R.id.textView_icon2);
        url_icon2.setMovementMethod(LinkMovementMethod.getInstance());

        return v;
    }

}