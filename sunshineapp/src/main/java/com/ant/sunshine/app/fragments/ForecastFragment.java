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
package com.ant.sunshine.app.fragments;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.TextView;

import com.ant.core.Utility;
import com.ant.sunshine.app.ForecastAdapter;
import com.ant.sunshine.app.R;
import com.ant.sunshine.app.test.WeatherContract;
import com.ant.sunshine.app.fragments.constants.ForecastConstants;
import com.ant.sunshine.app.sync.SunshineSyncAdapter;

/**
 * Encapsulates fetching the forecast and displaying it as a {@link android.support.v7.widget.RecyclerView} layout.
 */
public class ForecastFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>, SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String LOG_TAG = ForecastFragment.class.getSimpleName();
    public static final String ASC = " ASC";
    public static final String USE_TODAY_LAYOUT = "USE_TODAY_LAYOUT";
    private ForecastAdapter mForecastAdapter;
    private RecyclerView mRecyclerView;
    private boolean mUseTodayLayout, mAutoSelectView;
    private int mChoiceMode;
    private boolean mHoldForTransition;
    private long mInitialSelectedDate = -1;

    private static final String SELECTED_KEY = "selected_position";

    private static final int FORECAST_LOADER = 0;
    // For the forecast view we're showing only a small subset of the stored data.
    // Specify the columns we need.

    private Callback activityCallback;

    public static ForecastFragment newInstance(boolean useTodayLayout) {
        ForecastFragment forecastFragment = new ForecastFragment();
        Bundle args = new Bundle();
        args.putBoolean(USE_TODAY_LAYOUT, useTodayLayout);
        forecastFragment.setArguments(args);
        return forecastFragment;
    }

    public static ForecastFragment newInstance(boolean useTodayLayout, String locstring) {
        ForecastFragment forecastFragment = new ForecastFragment();
        Bundle args = new Bundle();
        args.putBoolean(USE_TODAY_LAYOUT, useTodayLayout);
        args.putString(SELECTED_KEY, locstring);
        forecastFragment.setArguments(args);
        return forecastFragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Callback) {
            this.activityCallback = (Callback) context;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add this line in order for this fragment to handle menu events.
        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getCurrentActivity());
        sp.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getCurrentActivity());
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
//        if (id == R.id.action_refresh) {
//            updateWeather();
//            return true;
//        }
        if (id == R.id.action_map) {
            openPreferredLocationInMap();
            return true;
        } else if (id == R.id.action_open_map) {
            openMap();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openMap() {
        if (activityCallback != null) {
            activityCallback.onOpenMap();
        }
    }

    @Override
    public void onInflate(Context activity, AttributeSet attrs, Bundle savedInstanceState) {
        super.onInflate(activity, attrs, savedInstanceState);
        TypedArray a = activity.obtainStyledAttributes(attrs, R.styleable.ForecastFragment,
                0, 0);
        mChoiceMode = a.getInt(R.styleable.ForecastFragment_android_choiceMode, AbsListView.CHOICE_MODE_NONE);
        mAutoSelectView = a.getBoolean(R.styleable.ForecastFragment_autoSelectView, false);
        mHoldForTransition = a.getBoolean(R.styleable.ForecastFragment_sharedElementTransitions, false);
        a.recycle();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        View emptyView = initRecycleView(rootView);
        initForecastAdapter(emptyView);
        initParallaxView(rootView);
        setAppBar(rootView);
        initUseTodayLayoutIfAvailable(savedInstanceState);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getArguments() != null) {
            String locString = getArguments().getString(SELECTED_KEY);
            if (!TextUtils.isEmpty(locString)) {
                saveStringLocationToPrefs(locString);
                onLocationChanged();
            }
        }
    }

    private void initUseTodayLayoutIfAvailable(Bundle savedInstanceState) {
        // If there's instance state, mine it for useful information.
        // The end-goal here is that the user never knows that turning their device sideways
        // does crazy lifecycle related things.  It should feel like some stuff stretched out,
        // or magically appeared to take advantage of room, but data or place in the app was never
        // actually *lost*.
        if (savedInstanceState != null) {
            mForecastAdapter.onRestoreInstanceState(savedInstanceState);
        }

        if (getArguments() != null) {
            mUseTodayLayout = getArguments().getBoolean(USE_TODAY_LAYOUT);
        }
        mForecastAdapter.setUseTodayLayout(mUseTodayLayout);
    }

    private View initRecycleView(View rootView) {
        // Get a reference to the RecyclerView, and attach this adapter to it.
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recyclerview_forecast);

        // Set the layout manager
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getCurrentActivity()));
        View emptyView = rootView.findViewById(R.id.recyclerview_forecast_empty);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);
        return emptyView;
    }

    private void initForecastAdapter(View emptyView) {
        // The ForecastAdapter will take data from a source and
        // use it to populate the RecyclerView it's attached to.
        mForecastAdapter = new ForecastAdapter(getCurrentActivity(), new ForecastAdapter.ForecastAdapterOnClickHandler() {
            @Override
            public void onClick(Long date, ForecastAdapter.ForecastAdapterViewHolder vh) {
                String locationSetting = Utility.getPreferredLocation(getCurrentActivity());
                activityCallback.onItemSelected(WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                        locationSetting, date),
                        vh
                );
            }
        }, emptyView, mChoiceMode);

        // specify an adapter (see also next example)
        mRecyclerView.setAdapter(mForecastAdapter);
    }

    private void initParallaxView(View rootView) {
        final View parallaxView = rootView.findViewById(R.id.parallax_bar);
        if (null != parallaxView) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        int max = parallaxView.getHeight();
                        if (dy > 0) {
                            parallaxView.setTranslationY(Math.max(-max, parallaxView.getTranslationY() - dy / 2));
                        } else {
                            parallaxView.setTranslationY(Math.min(0, parallaxView.getTranslationY() - dy / 2));
                        }
                    }
                });
            }
        }
    }

    private void setAppBar(View rootView) {
        final View includeToolBarView = rootView.findViewById(R.id.app_bar_layout);
        if (includeToolBarView != null) {
            final AppBarLayout appbarView = (AppBarLayout) includeToolBarView.findViewById(R.id.app_bar_layout);
            if (null != appbarView) {
                ViewCompat.setElevation(appbarView, 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                            if (0 == mRecyclerView.computeVerticalScrollOffset()) {
                                appbarView.setElevation(0);
                            } else {
                                appbarView.setElevation(appbarView.getTargetElevation());
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        // We hold for transition here just in-case the activity
        // needs to be re-created. In a standard return transition,
        // this doesn't actually make a difference.
        if (mHoldForTransition) {
            getCurrentActivity().supportPostponeEnterTransition();
        }
        getLoaderManager().initLoader(FORECAST_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    // since we read the location when we create the loader, all we need to do is restart things
    public void onLocationChanged() {
        getLoaderManager().restartLoader(FORECAST_LOADER, null, this);
    }

    private void openPreferredLocationInMap() {
        // Using the URI scheme for showing a location found on a map.  This super-handy
        // intent can is detailed in the "Common Intents" page of Android's developer site:
        // http://developer.android.com/guide/components/intents-common.html#Maps
        if (null != mForecastAdapter) {
            Cursor c = mForecastAdapter.getCursor();
            if (null != c) {
                c.moveToPosition(0);
                String posLat = c.getString(ForecastConstants.COL_COORD_LAT);
                String posLong = c.getString(ForecastConstants.COL_COORD_LONG);
                Uri geoLocation = Uri.parse("geo:" + posLat + "," + posLong);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(geoLocation);

                if (intent.resolveActivity(getCurrentActivity().getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Log.d(LOG_TAG, "Couldn't call " + geoLocation.toString() + ", no receiving apps installed!");
                }
            }

        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // When tablets rotate, the currently selected list item needs to be saved.

        if (mForecastAdapter != null) {
            mForecastAdapter.onSaveInstanceState(outState);
        }
        super.onSaveInstanceState(outState);
    }


    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // This is called when a new Loader needs to be created.  This
        // fragment only uses one loader, so we don't care about checking the id.

        // To only show current and future dates, filter the query to return weather only for
        // dates after or including today.

        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + ASC;

        String locationSetting = Utility.getPreferredLocation(getCurrentActivity());
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(locationSetting, System.currentTimeMillis());

        return new CursorLoader(getCurrentActivity(),
                weatherForLocationUri,
                ForecastConstants.FORECAST_COLUMNS,
                null,
                null,
                sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mForecastAdapter.swapCursor(data);
        updateEmptyView();
        if (data.getCount() == 0) {
            getCurrentActivity().supportStartPostponedEnterTransition();
        } else {
            mRecyclerView.getViewTreeObserver().addOnPreDrawListener(getAddOnPreDrawListener());
        }

    }

    @NonNull
    private ViewTreeObserver.OnPreDrawListener getAddOnPreDrawListener() {
        return new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // Since we know we're going to get items, we keep the listener around until
                // we see Children.
                if (mRecyclerView.getChildCount() > 0) {
                    mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                    int position = mForecastAdapter.getSelectedItemPosition();
                    if (position == RecyclerView.NO_POSITION && -1 != mInitialSelectedDate) {
                        Cursor data = mForecastAdapter.getCursor();
                        int count = data.getCount();
                        int dateColumn = data.getColumnIndex(WeatherContract.WeatherEntry.COLUMN_DATE);
                        for (int i = 0; i < count; i++) {
                            data.moveToPosition(i);
                            if (data.getLong(dateColumn) == mInitialSelectedDate) {
                                position = i;
                                break;
                            }
                        }
                    }
                    if (position == RecyclerView.NO_POSITION) {
                        position = 0;
                    }
                    // If we don't need to restart the loader, and there's a desired position to restore
                    // to, do so now.
                    mRecyclerView.smoothScrollToPosition(position);
                    RecyclerView.ViewHolder vh = mRecyclerView.findViewHolderForAdapterPosition(position);
                    if (null != vh && mAutoSelectView) {
                        mForecastAdapter.selectView(vh);
                    }
                    if (mHoldForTransition) {
                        getCurrentActivity().supportStartPostponedEnterTransition();
                    }
                    return true;
                }
                return false;
            }
        };
    }

    private AppCompatActivity getCurrentActivity() {
        return (AppCompatActivity) getActivity();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mRecyclerView) {
            mRecyclerView.clearOnScrollListeners();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mForecastAdapter.swapCursor(null);
    }

    public void setUseTodayLayout(boolean useTodayLayout) {
        mUseTodayLayout = useTodayLayout;
        if (mForecastAdapter != null) {
            mForecastAdapter.setUseTodayLayout(mUseTodayLayout);
        }
    }

    public void setInitialSelectedDate(long initialSelectedDate) {
        mInitialSelectedDate = initialSelectedDate;
    }

    /*
        Updates the empty list view with contextually relevant information that the user can
        use to determine why they aren't seeing weather.
     */
    private void updateEmptyView() {
        if (mForecastAdapter.getItemCount() == 0) {
            TextView tv = (TextView) getView().findViewById(R.id.recyclerview_forecast_empty);
            if (null != tv) {
                // if cursor is empty, why? do we have an invalid location
                int message = R.string.empty_forecast_list;
                @Utility.LocationStatus int location = Utility.getLocationStatus(getCurrentActivity());
                switch (location) {
                    case Utility.LOCATION_STATUS_SERVER_DOWN:
                        message = R.string.empty_forecast_list_server_down;
                        break;
                    case Utility.LOCATION_STATUS_SERVER_INVALID:
                        message = R.string.empty_forecast_list_server_error;
                        break;
                    case Utility.LOCATION_STATUS_INVALID:
                        message = R.string.empty_forecast_list_invalid_location;
                        break;
                    default:
                        if (!Utility.isNetworkAvailable(getCurrentActivity())) {
                            message = R.string.empty_forecast_list_no_network;
                        }
                }
                tv.setText(message);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_location_status_key))) {
            updateEmptyView();
        }
    }

    private void saveStringLocationToPrefs(String placeString) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(getString(R.string.pref_location_key), placeString);
        editor.apply();
    }


    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callback {
        /**
         * DetailFragmentCallback for when an item has been selected.
         */
        void onItemSelected(Uri dateUri, ForecastAdapter.ForecastAdapterViewHolder vh);

        void onOpenMap();
    }
}