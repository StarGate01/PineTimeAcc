package de.chrz.pinetimeacc.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<String> mTitle;
    private final MutableLiveData<String> mDelta;
    private final MutableLiveData<String> mFreq;

    public HomeViewModel() {
        mTitle = new MutableLiveData<>();
        mTitle.setValue("");
        mDelta = new MutableLiveData<>();
        mDelta.setValue("0");
        mFreq = new MutableLiveData<>();
        mFreq.setValue("0");
    }

    public MutableLiveData<String> getTitle() {
        return mTitle;
    }

    public MutableLiveData<String> getFreq() {
        return mFreq;
    }

    public MutableLiveData<String> getDelta() {
        return mDelta;
    }

}