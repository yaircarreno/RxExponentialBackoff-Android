# RxExponentialBackoff Pattern implemented in Android using RxJava

This repository contains an example of the retry pattern implementation in Android applications.
You can also find the iOS implementation at [RxExponentialBackoff-iOS](https://github.com/yaircarreno/RxExponentialBackoff-iOS)

## Articles

- [Exponential Backoff and Retry Patterns in Mobile](https://www.yaircarreno.com/2021/03/exponential-backoff-and-retry-patterns.html)


## Component Diagram

![RxExponentialBackoff Pattern](https://github.com/yaircarreno/RxExponentialBackoff-Android/blob/main/screenshots/exb_retry_pattern.png)

## Implementation

With constant incremental time window approach:

```java
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
```

With exponential and random increment time window approach:

```java
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
```

Function to calculate the delay time used in the pattern retry.

```java
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
```


## Demo

![RxExponentialBackoff Pattern](https://github.com/yaircarreno/RxExponentialBackoff-Android/blob/main/screenshots/demo-android-retry-pattern.gif)


## Versions of IDEs and technologies used.

- Android Studio 4.1.2 - Java 8
- RxJava 3 - RxAndroid 3
- Activities - Layouts


