package com.yaircarreno.rxexponentialbackoff;

import androidx.appcompat.app.AppCompatActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import com.yaircarreno.rxexponentialbackoff.databinding.ActivityMainBinding;

import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private CompositeDisposable compositeDisposable;

    @SuppressLint("SimpleDateFormat")
    private static final DateFormat dateFormat = new SimpleDateFormat("mm:ss");

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        if (compositeDisposable == null) {
            compositeDisposable = new CompositeDisposable();
        }
        setUpUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (compositeDisposable != null) {
            compositeDisposable.clear();
        }
    }

    private void setUpUI() {
        binding.button1.setOnClickListener(view1 -> {
            prepareUIToCall();
            immediateRetry(2);
        });
        binding.button2.setOnClickListener(view1 -> {
            prepareUIToCall();
            constantRetry(3);
        });
        binding.button3.setOnClickListener(view1 -> {
            prepareUIToCall();
            exponentialRetry(3);
        });
        binding.button4.setOnClickListener(view1 -> {
            prepareUIToCall();
            exponentialWithRandomRetry(3);
        });
    }

    private void immediateRetry(final int maxiTimeRetry) {
        compositeDisposable.add(
                this.operationWithPossibleFailure()
                        .doOnError(throwable -> Log.d(TAG, "retrying..."))
                        .retry(maxiTimeRetry)
                        .onErrorResumeNext(throwable -> {
                            Log.d(TAG, Objects.requireNonNull(throwable.getMessage()));
                            return Observable.error(throwable);
                        })
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(user -> logs(user, true),
                                throwable -> logs(throwable, false),
                                () -> Log.d(TAG, "completed")));
    }

    private void constantRetry(final int maxiTimeRetry) {
        compositeDisposable.add(
                this.operationWithPossibleFailure()
                        .retryWhen(errors -> errors
                                .doOnNext(ignored -> Log.d(TAG, "retrying..."))
                                .delay(2, TimeUnit.SECONDS)
                                .take(maxiTimeRetry)
                                .concatWith(Observable.error(new Throwable())))
                        .onErrorResumeNext(Observable::error)
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(user -> logs(user, true),
                                throwable -> logs(throwable, false),
                                () -> Log.d(TAG, "completed")));
    }


    private void exponentialRetry(final int maxiTimeRetry) {
        compositeDisposable.add(
                this.operationWithPossibleFailure()
                        .retryWhen(errors -> errors
                                .map(throwable -> 1)
                                .scan((attempt, next) -> attempt + next)
                                .map(attempt -> new Pair<>(attempt, this.exponentialBackoff(attempt, false)))
                                .doOnNext(pair -> this.printCurrentTime(pair.first, pair.second))
                                .map(pair -> pair.second)
                                .flatMap(delayTime ->
                                        Observable.just(delayTime)
                                                .delay(delayTime, TimeUnit.SECONDS))
                                .take(maxiTimeRetry)
                                .concatWith(Observable.error(new Throwable("unexpected error in service"))))
                        .onErrorResumeNext(Observable::error)
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(user -> logs(user, true),
                                throwable -> logs(throwable, false),
                                () -> Log.i(TAG, "completed")));
    }

    private void exponentialWithRandomRetry(final int maxiTimeRetry) {
        compositeDisposable.add(
                this.operationWithPossibleFailure()
                        .retryWhen(errors -> errors
                                .map(throwable -> 1)
                                .scan((attempt, next) -> attempt + next)
                                .map(attempt -> new Pair<>(attempt, this.exponentialBackoff(attempt, true)))
                                .doOnNext(pair -> this.printCurrentTime(pair.first, pair.second))
                                .map(pair -> pair.second)
                                .flatMap(delayTime ->
                                        Observable.just(delayTime)
                                                .delay(delayTime, TimeUnit.SECONDS))
                                .take(maxiTimeRetry)
                                .concatWith(Observable.error(new Throwable("unexpected error in service"))))
                        .onErrorResumeNext(Observable::error)
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(user -> logs(user, true),
                                throwable -> logs(throwable, false),
                                () -> Log.i(TAG, "completed")));
    }

    /*
     * exponentialBackoff: function to calculate the delay time used in the pattern retry.
     */
    private long exponentialBackoff(final int attempt, final boolean withRandom) {
        final long min = -1000L;
        final long max = 1000L;
        if (withRandom) {
            long random_number = min + (long) (Math.random() * (max - min));
            double random_number_milliseconds = random_number * 0.001;
            return (long) (Math.pow(2, attempt) + random_number_milliseconds);
        } else {
            return (long) Math.pow(2, attempt);
        }
    }

    private <T> Observable<T> operationWithPossibleFailure() {
        return Observable.error(new Throwable("unexpected error in service"));
    }

    private void printCurrentTime(int attempt, long timeElapsed) {
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = new Date();
        Log.d(TAG, "Retrying " + attempt + " occurs at " + dateFormat.format(date) + " time, after " + timeElapsed + " seconds");
    }

    private <T> void logs(T response, boolean success) {
        if (success) {
            Log.d(TAG, "next: " + response);
        } else {
            Log.e(TAG, "error: " + response);
        }
        showLoading(false);
        enableButtons(true);
    }

    private void prepareUIToCall() {
        enableButtons(false);
        showLoading(true);
    }

    private void showLoading(boolean show) {
        binding.progressBar1.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void enableButtons(boolean enable) {
        binding.button1.setEnabled(enable);
        binding.button2.setEnabled(enable);
        binding.button3.setEnabled(enable);
        binding.button4.setEnabled(enable);
    }
}