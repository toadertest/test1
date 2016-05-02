package com.ant.sunshine.app.location;

import android.content.Context;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.ant.sunshine.app.fragments.MapLocationFragment;
import com.ant.sunshine.app.location.services.GoogleLocationServicesUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.maps.android.SphericalUtil;

/**
 * Created by andrei on 4/1/16.
 */
public class LocationTrackingManager {

    private LocationManager locationManager;
    private LocationListener requestLastLocation;
    private final GoogleApiClient locationClient;
    private static final String TAG = LocationTrackingManager.class.getCanonicalName();
    private final Context context;
    private LatLng defaultLocationSouth;
    private LatLng getDefaultLocationNorth;
//    private MapLocationFragment.GpsCallback callback;

    private int radius = 100;

    private Handler handler;

    public LocationTrackingManager(@NonNull Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
        locationClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addConnectionCallbacks(connectionCallbacks)
                .addOnConnectionFailedListener(onConnectionFailedListener)
                .build();
        locationClient.connect();
        defaultLocationSouth = new LatLng(77.039, -66.254);
        getDefaultLocationNorth = new LatLng(-73.15, -15.39);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    private final GoogleApiClient.OnConnectionFailedListener onConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            String message = connectionResult.getErrorMessage();
            warnUserPermissionNotSet(message);
        }
    };

    private final GoogleApiClient.ConnectionCallbacks connectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {

        @Override
        public void onConnected(Bundle bundle) {
            if (requestLastLocation != null && locationClient.isConnected()) {
                try {
                    requestLastLocation.onLocationChanged(LocationServices.FusedLocationApi.getLastLocation(locationClient));
                } catch (SecurityException secex) {
                    warnUserPermissionNotSet(secex.getMessage());
                }
                requestLastLocation = null;
            }
        }

        @Override
        public void onConnectionSuspended(int i) {

        }
    };

    private void warnUserPermissionNotSet(final String messageExc) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "Permission not given for location in manifest", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Error message: " + messageExc);
            }
        });
    }


    public void requestLastLocation(final LocationListener locationListener) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isAllowed()) {
                    requestLastLocation = null;
                    locationListener.onLocationChanged(null);
                } else {
                    requestLastLocation = locationListener;
                    connectionCallbacks.onConnected(null);
                }
            }
        });
    }

    /**
     * Returns true if gps provider is enabled.
     */
    public boolean isGpsProviderEnabled() {
        if (!isAllowed()) {
            return false;
        }
        String provider = LocationManager.GPS_PROVIDER;
        if (locationManager.getProvider(provider) == null) {
            return false;
        }
        return locationManager.isProviderEnabled(provider);
    }

    public boolean isAllowed() {
        return GoogleLocationServicesUtils.isAllowed(context);
    }

    public boolean isConnected() {
        return locationClient.isConnected();
    }

    public GoogleApiClient getLocationClient() {
        return locationClient;
    }

    public LatLngBounds getLatLngBounds() {
        final LatLng northeast = SphericalUtil.computeOffset(defaultLocationSouth, radius * Math.sqrt(2.0), 225);
        final LatLng southeast = SphericalUtil.computeOffset(getDefaultLocationNorth, radius * Math.sqrt(2.0), 45);
        return new LatLngBounds(southeast, northeast);
    }

    public void askToEnableGps(MapLocationFragment.GpsCallback callback) {
        // this.callback = callback;
        if (locationClient == null) {
            return;
        }
        mLocationRequestHighAccuracy = LocationRequest.create();
        mLocationRequestHighAccuracy.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequestHighAccuracy.setInterval(30 * 1000);
        mLocationRequestHighAccuracy.setFastestInterval(5 * 1000);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequestHighAccuracy);
        builder.setAlwaysShow(true);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(locationClient, builder.build());

        result.setResultCallback(getResultCallback(callback));
    }

    private ResultCallback getResultCallback(final MapLocationFragment.GpsCallback callback) {

        return new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates statesResult = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        callback.onConnected();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        callback.onAskToConnect(status);
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        callback.onDisconnected();
                        break;
                }
            }
        };
    }

    LocationRequest mLocationRequestHighAccuracy = new LocationRequest();
}
