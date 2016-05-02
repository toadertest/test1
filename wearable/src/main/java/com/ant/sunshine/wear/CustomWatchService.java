package com.ant.sunshine.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import com.ant.core.WatchfaceSyncCommons;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;

public class CustomWatchService extends CanvasWatchFaceService {

    private static final long TICK_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new SimpleEngine();
    }

    private class SimpleEngine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

        private static final String ACTION_TIME_ZONE = "time-zone";
        private static final String TAG = "SimpleEngine";

        private CustomWatchFace watchFace;
        private Handler timeTick;
        private GoogleApiClient googleApiClient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(CustomWatchService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            timeTick = new Handler(Looper.myLooper());
            startTimerIfNecessary();

            watchFace = CustomWatchFace.newInstance(CustomWatchService.this);
            googleApiClient = new GoogleApiClient.Builder(CustomWatchService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            googleApiClient.connect();
        }

        private void startTimerIfNecessary() {
            timeTick.removeCallbacks(timeRunnable);
            if (isVisible() && !isInAmbientMode()) {
                timeTick.post(timeRunnable);
            }
        }

        private final Runnable timeRunnable = new Runnable() {
            @Override
            public void run() {
                onSecondTick();
                if (isVisible() && !isInAmbientMode()) {
                    timeTick.postDelayed(this, TICK_PERIOD_MILLIS);
                }
            }
        };

        private void onSecondTick() {
            invalidateIfNecessary();
        }

        private void invalidateIfNecessary() {
            if (isVisible() && !isInAmbientMode()) {
                invalidate();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                Log.d(TAG, "visible:" + visible);
                Log.d(TAG, "connecting to google client.");
                registerTimeZoneReceiver();
                googleApiClient.connect();
            } else {
                unregisterTimeZoneReceiver();
                releaseGoogleApiClient();
            }

            startTimerIfNecessary();
        }

        private void releaseGoogleApiClient() {
            if (googleApiClient != null && googleApiClient.isConnected()) {
                Wearable.DataApi.removeListener(googleApiClient, onDataChangedListener);
                googleApiClient.disconnect();
            }
        }

        private void unregisterTimeZoneReceiver() {
            unregisterReceiver(timeZoneChangedReceiver);
        }

        private void registerTimeZoneReceiver() {
            IntentFilter timeZoneFilter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(timeZoneChangedReceiver, timeZoneFilter);
        }

        private BroadcastReceiver timeZoneChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
                    watchFace.updateTimeZoneWith(intent.getStringExtra(ACTION_TIME_ZONE));
                }
            }
        };

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);
            watchFace.draw(canvas, bounds);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            watchFace.setAntiAlias(!inAmbientMode);
            watchFace.setShowSeconds(!isInAmbientMode());

            if (inAmbientMode) {
                watchFace.updateAmbient(true);
            } else {
                watchFace.updateAmbient(false);
            }

            invalidate();

            startTimerIfNecessary();
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(TAG, "connected GoogleAPI");

            Wearable.DataApi.addListener(googleApiClient, onDataChangedListener);
            Wearable.DataApi.getDataItems(googleApiClient).setResultCallback(onConnectedResultCallback);
        }

        private final DataApi.DataListener onDataChangedListener = new DataApi.DataListener() {
            @Override
            public void onDataChanged(DataEventBuffer dataEvents) {
                for (DataEvent event : dataEvents) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        DataItem item = event.getDataItem();
                        processConfigurationFor(item);
                    }
                }

                dataEvents.release();
                invalidateIfNecessary();
            }
        };

        private void processConfigurationFor(DataItem item) {
            Log.d(TAG, "New data received");
            if (WatchfaceSyncCommons.PATH.equals(item.getUri().getPath())) {
                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                if (dataMap.containsKey(WatchfaceSyncCommons.KEY_LOW_TEMP)) {
                    Double lowValue = dataMap.getDouble(WatchfaceSyncCommons.KEY_LOW_TEMP);
                    watchFace.updateLowValue(lowValue);
                }

                if (dataMap.containsKey(WatchfaceSyncCommons.KEY_HIGH_TEMP)) {
                    Double highValue = dataMap.getDouble(WatchfaceSyncCommons.KEY_HIGH_TEMP);
                    watchFace.updateHighValue(highValue);
                }

                if (dataMap.containsKey(WatchfaceSyncCommons.KEY_WEATHER_INFO)) {
                    int drawableId = dataMap.getInt(WatchfaceSyncCommons.KEY_WEATHER_INFO);
                    watchFace.updateWeatherDrawable(drawableId);
                }
            }
        }

        private final ResultCallback<DataItemBuffer> onConnectedResultCallback = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(DataItemBuffer dataItems) {
                for (DataItem item : dataItems) {
                    processConfigurationFor(item);
                }

                dataItems.release();
                invalidateIfNecessary();
            }
        };

        @Override
        public void onConnectionSuspended(int i) {
            Log.e(TAG, "suspended GoogleAPI");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.e(TAG, "connectionFailed GoogleAPI");
        }

        @Override
        public void onDestroy() {
            timeTick.removeCallbacks(timeRunnable);
            releaseGoogleApiClient();

            super.onDestroy();
        }
    }
}
