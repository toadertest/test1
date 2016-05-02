/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ant.sunshine.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.Pair;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.ant.core.Utility;
import com.ant.sunshine.app.adapter.PlacesAdapter;
import com.ant.sunshine.app.test.WeatherContract;
import com.ant.sunshine.app.fragments.DetailFragment;
import com.ant.sunshine.app.ForecastAdapter;
import com.ant.sunshine.app.fragments.ForecastFragment;
import com.ant.sunshine.app.R;
import com.ant.sunshine.app.fragments.MapLocationFragment;
import com.ant.sunshine.app.gcm.RegistrationIntentService;
import com.ant.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceBuffer;
import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ForecastFragment.Callback, MapLocationFragment.Callback, FragmentManager.OnBackStackChangedListener, GoogleApiClient.OnConnectionFailedListener {

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String DETAILFRAGMENT_TAG = "DFTAG";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static final String SENT_TOKEN_TO_SERVER = "sentTokenToServer";

    private boolean mTwoPane;
    private LatLng mLocationLatLng;
    private MenuItem searchViewItem;

    private String mLocation;
    private PlacesAdapter adapter;
    /**
     * GoogleApiClient wraps our service connection to Google Play Services and provides access
     * to the user's sign in state as well as the Google's APIs.
     */
    private int previousStackCount;
    private SearchView searchView;
    private static final String TAG = MainActivity.class.getCanonicalName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //mLocationLatLng = Utility.getLocation();
        mLocation = Utility.getPreferredLocation(this);
        Uri contentUri = getIntent() != null ? getIntent().getData() : null;

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.app_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        if (findViewById(R.id.weather_detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {
                DetailFragment fragment = new DetailFragment();
                if (contentUri != null) {
                    Bundle args = new Bundle();
                    args.putParcelable(DetailFragment.DETAIL_URI, contentUri);
                    fragment.setArguments(args);
                }
                getFragmentManager().beginTransaction()
                        .replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
                        .commit();
            }
        } else {
            mTwoPane = false;
            getSupportActionBar().setElevation(0f);
        }
        initFragment(contentUri);
        SunshineSyncAdapter.initializeSyncAdapter(this);
        registerToGcm();
    }

    private void registerToGcm() {
        // If Google Play Services is up to date, we'll want to register GCM. If it is not, we'll
        // skip the registration and this device will not receive any downstream messages from
        // our fake server. Because weather alerts are not a core feature of the app, this should
        // not affect the behavior of the app, from a user perspective.
        if (checkPlayServices()) {
            // Because this is the initial creation of the app, we'll want to be certain we have
            // a token. If we do not, then we will start the IntentService that will register this
            // application with GCM.
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(this);
            boolean sentToken = sharedPreferences.getBoolean(SENT_TOKEN_TO_SERVER, false);
            if (!sentToken) {
                Intent intent = new Intent(this, RegistrationIntentService.class);
                startService(intent);
            }
        }
    }

    private void initFragment(Uri contentUri) {
        Fragment fragment = getFragmentById(R.id.fragment_container);
        if (isForecastFragment(fragment)) {
            if (contentUri != null) {
                ((ForecastFragment) fragment).setInitialSelectedDate(WeatherContract.WeatherEntry.getDateFromUri(contentUri));
            }
            ((ForecastFragment) fragment).setUseTodayLayout(!mTwoPane);
        } else {
            if (fragment == null) {
                fragment = ForecastFragment.newInstance(!mTwoPane);
                setForecastFragment((ForecastFragment) fragment);
            }
        }
    }

    private boolean isForecastFragment(Fragment fragment) {
        return fragment != null && fragment instanceof ForecastFragment;
    }

    private boolean isMapLocationFragment(Fragment fragment) {
        return fragment != null && fragment instanceof MapLocationFragment;
    }

    @NonNull
    private void setForecastFragment(ForecastFragment fragment) {
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
    }

    public void showMap() {

        MapLocationFragment locationFragment = MapLocationFragment.newInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, locationFragment)
                .addToBackStack(null)
                .commit();
    }

    private Fragment getFragmentById(int id) {
        return getSupportFragmentManager().findFragmentById(id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        searchViewItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchViewItem);
        if (searchView != null) {
            initSearchView();
        }
        return true;
    }

    private void initSearchView() {

        AutoCompleteTextView autoCompleteTextView = (AutoCompleteTextView) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        autoCompleteTextView.setOnItemClickListener(mAutocompleteClickListener);

        adapter = new PlacesAdapter(this, null);

        autoCompleteTextView.setAdapter(adapter);
        searchView.setOnQueryTextListener(getOnQueryTextListener());
        searchView.setIconifiedByDefault(true);

    }

    private SearchView.OnQueryTextListener getOnQueryTextListener() {
        return new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {

                clearSearchView();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        };
    }

    private void clearSearchView() {
        if (searchView != null && searchViewItem != null) {
            searchView.setQuery("", false);
            searchViewItem.collapseActionView();
            searchView.clearFocus();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //LatLng location = Utility.getLocation();
        String location = Utility.getPreferredLocation(this);
        // update the location in our second pane using the fragment manager
        reloadLocationIfChanged(location);
    }

    private void reloadLocationIfChanged(String location) {
        if (location != null && !location.equals(mLocation)) {
            Fragment fragment = getFragmentById();
            if (isForecastFragment(fragment)) {
                reloadLocation();
                updateLocationForecast(location);
            } else {
                MapLocationFragment mapLocationFragment = (MapLocationFragment) fragment;
                if (mapLocationFragment != null) {
                    try {
                        Geocoder geocoder = new Geocoder(this);
                        List<Address> addressList = geocoder.getFromLocationName(location, 1);
                        Address address = addressList.get(0);
                        Location loc = new Location("");
                        loc.setLatitude(address.getLatitude());
                        loc.setLongitude(address.getLongitude());
                        mapLocationFragment.getLocationListener().onLocationChanged(loc);
                    } catch (IOException ioex) {
                        Toast.makeText(this, "Exception in loading the address!", Toast.LENGTH_SHORT).show();
                    } catch (Exception exc) {
                        Toast.makeText(this, "Exception in loading the address!", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            mLocation = location;
        }
    }

    private void updateLocationForecast(String location) {
        ForecastFragment ff = (ForecastFragment) getFragmentById();
        if (null != ff) {
            ff.onLocationChanged();
        }
        DetailFragment df = (DetailFragment) getFragmentManager().findFragmentByTag(DETAILFRAGMENT_TAG);
        if (null != df) {
            df.onLocationChanged(location);
        }
    }

    private void saveLocationToPrefs(Place place) {
        saveStringLocationToPrefs(place.getAddress().toString());
    }

    private void saveStringLocationToPrefs(String placeString) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(getString(R.string.pref_location_key), placeString);
        editor.apply();
    }

    private Fragment getFragmentById() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    }

    @Override
    public void onItemSelected(Uri contentUri, ForecastAdapter.ForecastAdapterViewHolder vh) {
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            Bundle args = new Bundle();
            args.putParcelable(DetailFragment.DETAIL_URI, contentUri);

            DetailFragment fragment = new DetailFragment();
            fragment.setArguments(args);

            getFragmentManager().beginTransaction()
                    .replace(R.id.weather_detail_container, fragment, DETAILFRAGMENT_TAG)
                    .commit();
        } else {
            Intent intent = new Intent(this, DetailActivity.class)
                    .setData(contentUri);

            ActivityOptionsCompat activityOptions =
                    ActivityOptionsCompat.makeSceneTransitionAnimation(this,
                            new Pair<View, String>(vh.mIconView, getString(R.string.detail_icon_transition_name)));
            ActivityCompat.startActivity(this, intent, activityOptions.toBundle());
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(LOG_TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    @Override
    public void onOpenMap() {
        showMap();
    }

    @Override
    public void onBackStackChanged() {
        int localStackCount = previousStackCount;

        previousStackCount = getSupportFragmentManager().getBackStackEntryCount();

        boolean popBackStack = previousStackCount < localStackCount;
        if (popBackStack) {
            Log.d(TAG, "popping out");
            if (previousStackCount == 0) {
                finish();
            }
        }
    }

    /**
     * Listener that handles selections from suggestions from the AutoCompleteTextView that
     * displays Place suggestions.
     * Gets the place id of the selected item and issues a request to the Places Geo Data API
     * to retrieve more details about the place.
     *
     * @see com.google.android.gms.location.places.GeoDataApi#getPlaceById(com.google.android.gms.common.api.GoogleApiClient,
     * String...)
     */
    private AdapterView.OnItemClickListener mAutocompleteClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            /*
             Retrieve the place ID of the selected item from the Adapter.
             The adapter stores each Place suggestion in a AutocompletePrediction from which we
             read the place ID and title.
              */
            final AutocompletePrediction item = adapter.getItem(position);
            final String placeId = item.getPlaceId();

             /*
             Issue a request to the Places Geo Data API to retrieve a Place object with additional
             details about the place.
              */
            PendingResult<PlaceBuffer> placeResult = adapter.getPlaceById(placeId);
            placeResult.setResultCallback(mUpdatePlaceDetailsCallback);
        }
    };

    /**
     * GpsCallback for results from a Places Geo Data API query that shows the first place result in
     * the details view on screen.
     */
    private ResultCallback<PlaceBuffer> mUpdatePlaceDetailsCallback = new ResultCallback<PlaceBuffer>() {
        @Override
        public void onResult(PlaceBuffer places) {
            if (!places.getStatus().isSuccess()) {
                // Request did not complete successfully
                Log.e(TAG, "Place query did not complete. Error: " + places.getStatus().toString());
                places.release();
                return;
            }
            // Get the Place object from the buffer.
            final Place place = places.get(0);
            saveLocationToPrefs(place);
            reloadLocationIfChanged(place.getAddress().toString());
            clearSearchView();
            Log.i(TAG, "Place details received: " + place.getName());
            places.release();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case MapLocationFragment.CONNECTION_RESOLUTION_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Fragment fragment = getFragmentById();
                        if (isMapLocationFragment(fragment)) {
                            ((MapLocationFragment) fragment).initGpsTracker(false);
                        }
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(this, "Gps not enabled:", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        Toast.makeText(this, "Gps not enabled:", Toast.LENGTH_SHORT).show();
                        break;
                }
                break;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onUpdateLocation(Location location) {

        String locString = "";
        Geocoder geocoder = new Geocoder(this);

        try {
            List<Address> addressesList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            locString = addressesList.get(0).getLocality();
            locString += ", ";
            locString += addressesList.get(0).getCountryCode();
        } catch (IOException e) {
            locString = null;
        }
        if (locString != null && !locString.isEmpty()) {
            reloadLocation();
            saveStringLocationToPrefs(locString);
            Fragment fragment = ForecastFragment.newInstance(!mTwoPane, locString);
            setForecastFragment((ForecastFragment) fragment);
        }

    }

    private void reloadLocation() {
        // we've changed the location
        // first clear locationStatus
        Utility.resetLocationStatus(this);
        SunshineSyncAdapter.syncImmediately(this);
    }

    @Override
    protected void onStop() {
        SunshineSyncAdapter.stopSyncing();
        super.onStop();
    }
}
