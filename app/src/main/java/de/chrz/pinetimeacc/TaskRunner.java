package de.chrz.pinetimeacc;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TaskRunner {
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface Callback<R> {
        void onComplete(R result);
    }

    public <Result> void executeAsync(Callable<Result> callable, Callback<Result> callback) {
        executor.execute(() -> {
            final Result result;
            try {
                result = callable.call();
                handler.post(() -> callback.onComplete(result));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}