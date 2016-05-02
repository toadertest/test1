package com.ant.sunshine.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.ant.sunshine.app.R;
import com.ant.sunshine.app.activities.MainActivity;
import com.ant.sunshine.app.location.LocationTrackingManager;
import com.ant.sunshine.app.location.services.GoogleLocationServicesUtils;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

/**
 * Created by andrei on 4/1/16.
 */
public class MapLocationFragment extends Fragment {

    private static final float DEFAULT_ZOOM_LEVEL = 16f;
    private static final String TAG = MapLocationFragment.class.getSimpleName();
    private static final double DEFAULT_LATITUDE = 68.319392;
    private static final double DEFAULT_LONGITUDE = 14.407718;
    private LocationTrackingManager mGPSLiveTrackerLocManager;
    private GoogleMap googleMap;
    private boolean isFirst;
    private LocationSource.OnLocationChangedListener onLocationChangedListener;
    private Location currentLocation;
    private FloatingActionButton actionButton;

    public static final int CONNECTION_RESOLUTION_CODE = 100;

    private Callback callback;

    public static MapLocationFragment newInstance() {
        MapLocationFragment f = new MapLocationFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.maps_layout, container, false);

        actionButton = (FloatingActionButton) view.findViewById(R.id.fab);
        actionButton.setOnClickListener(getOnClickListener());
        return view;
    }

    private View.OnClickListener getOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLocation != null) {
                    callback.onUpdateLocation(currentLocation);
                }
            }
        };
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (getActivity() instanceof Callback) {
            callback = (Callback) getActivity();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initializeMapIfNeeded();
    }

    private void initializeMapIfNeeded() {
        if (googleMap == null) {
            loadMapAsync();
        }
    }

    private void loadMapAsync() {
        ((SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.location_map)).getMapAsync(getOnMapReadyCallback());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.map_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.map_type_normal) {
            if (googleMap != null) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            }
            return true;
        }

        if (item.getItemId() == R.id.map_type_hybrid) {
            if (googleMap != null) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            }
            return true;
        }

        if (item.getItemId() == R.id.map_type_satellite) {
            if (googleMap != null) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            }
            return true;
        } else {
            if (googleMap != null) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            }
            return true;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mGPSLiveTrackerLocManager = new LocationTrackingManager(getActivity());
        initGpsTracker(true);
    }

    public synchronized void initGpsTracker(boolean force) {
        boolean isGPSProviderEnabled = mGPSLiveTrackerLocManager.isGpsProviderEnabled();
        if (googleMap != null) {
            try {
                if (isGPSProviderEnabled) {
                    googleMap.setMyLocationEnabled(true);
                } else if (force) {
                    askToEnableGps();
                }
            } catch (SecurityException secex) {
                Toast.makeText(getActivity(), "not enabled in manifest", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void askToEnableGps() {
        mGPSLiveTrackerLocManager.askToEnableGps(locationCallback);
    }

    private GpsCallback locationCallback = new GpsCallback() {
        @Override
        public void onConnected() {
            initGpsTracker(false);
        }

        @Override
        public void onAskToConnect(Status status) {
            try {
                status.startResolutionForResult(getActivity(), CONNECTION_RESOLUTION_CODE);
            } catch (IntentSender.SendIntentException setex) {
                Toast.makeText(getContext(), "Exception in sending intent:", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onDisconnected() {
            Toast.makeText(getContext(), "Location services unavailable!", Toast.LENGTH_SHORT).show();
        }
    };


    private void requestLastLocationOrNotify() {
        if (mGPSLiveTrackerLocManager != null && !mGPSLiveTrackerLocManager.isGpsProviderEnabled()) {
            notifyUserNoLocationIsAvailable();
        } else {
            requestLastKnowLocation();
        }
    }

    private void requestLastKnowLocation() {
        mGPSLiveTrackerLocManager.requestLastLocation(getLocationListener());
    }

    protected LocationSource getNewLocationSource() {
        return new LocationSource() {

            @Override
            public void activate(OnLocationChangedListener listener) {
                onLocationChangedListener = listener;
            }

            @Override
            public void deactivate() {
                onLocationChangedListener = null;
            }
        };
    }

    /**
     * in case the user wants to know its location and the gps is disabled then a notification will be shown
     */
    protected void notifyUserNoLocationIsAvailable() {
        String message = GoogleLocationServicesUtils.getGpsDisabledMyLocationMessage(getActivity());
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
    }

    public LocationListener getLocationListener() {
        return new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (isResumed()) {
                    isFirst = setCurrentLocation(location);
                    updateCurrentLocation(isFirst);
                }
            }
        };
    }

    private boolean setCurrentLocation(Location location) {
        if (location != null && currentLocation == null) {
            isFirst = true;
        }
        currentLocation = location;
        return isFirst;
    }

    public OnMapReadyCallback getOnMapReadyCallback() {
        return new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap map) {
                googleMap = map;
                if (googleMap == null) {
                    return;
                }

                googleMap.setIndoorEnabled(true);
                googleMap.getUiSettings().setMyLocationButtonEnabled(true);
                googleMap.setOnMyLocationButtonClickListener(getMapMpOnMyLocationClickListener());
                googleMap.setLocationSource(getNewLocationSource());
                googleMap.setOnCameraChangeListener(getCameraChangeListener());
                initGpsTracker(true);
            }
        };
    }

    private GoogleMap.OnMyLocationButtonClickListener getMapMpOnMyLocationClickListener() {
        return new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                requestLastLocationOrNotify();
                return true;
            }
        };
    }

    private GoogleMap.OnCameraChangeListener getCameraChangeListener() {
        return new GoogleMap.OnCameraChangeListener() {

            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                if (isResumed() && currentLocation != null && !isLocationVisible(currentLocation)) {
                    googleMap.animateCamera(CameraUpdateFactory.zoomBy(DEFAULT_ZOOM_LEVEL));
                    googleMap.setOnCameraChangeListener(getCameraChangeListener());
                }
            }
        };
    }

    /**
     * Updates the current location.
     *
     * @param forceZoom true to force zoom to the current location regardless of
     *                  the keepCurrentLocationVisible policy
     */
    protected void updateCurrentLocation(final boolean forceZoom) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                updateLocationInternal(forceZoom);
            }
        });
    }

    /**
     * updates the current location according to the forceZoom parameter.
     * Technically speaking, the forceZoom is the first time the my location button is triggered, or, in case of a resume.
     *
     * @param forceZoom true force, false otherwise
     */
    private void updateLocationInternal(boolean forceZoom) {
        if (!isResumed() || googleMap == null || onLocationChangedListener == null
                || currentLocation == null) {
            Log.d(TAG, "isResumed or google map or locationchanged listener or current location is null");
            return;
        }
        onLocationChangedListener.onLocationChanged(currentLocation);
        if (forceZoom || !isLocationVisible(currentLocation)) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            CameraUpdate cameraUpdate = forceZoom ? CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL) : CameraUpdateFactory.newLatLng(latLng);
            googleMap.animateCamera(cameraUpdate);
        }
    }


    private boolean isLocationVisible(Location location) {
        if (location == null || googleMap == null) {
            return false;
        }
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        return googleMap.getProjection().getVisibleRegion().latLngBounds.contains(latLng);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CONNECTION_RESOLUTION_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        initGpsTracker(false);
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(getContext(), "Gps not enabled:", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        break;
                }
                break;
        }
    }

    public interface GpsCallback {
        void onConnected();

        void onAskToConnect(Status status);

        void onDisconnected();
    }


    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callback {

        void onUpdateLocation(Location location);
    }
}
