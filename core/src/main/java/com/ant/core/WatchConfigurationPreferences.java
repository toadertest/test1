package com.ant.core;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class WatchConfigurationPreferences implements Parcelable {

    public static final String PATH = "/simple_watch_face_config";

    private static final String NAME = "WatchConfigurationPreferences";

    private static final double DEFAULT_LOW_TEMP = 10.0d;
    private static final double DEFAULT_HIGH_TEMP = 15.0d;
    private static final int DEFAULT_WEATHER_INFO = 30;
    private final Bundle mBundle;


    WatchConfigurationPreferences(Bundle bundle) {
        this.mBundle = bundle;
    }

    protected WatchConfigurationPreferences(Parcel in) {
        mBundle = in.readBundle();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(mBundle);
    }

    public static final Creator<WatchConfigurationPreferences> CREATOR = new Creator<WatchConfigurationPreferences>() {
        @Override
        public WatchConfigurationPreferences createFromParcel(Parcel in) {
            return new WatchConfigurationPreferences(in);
        }

        @Override
        public WatchConfigurationPreferences[] newArray(int size) {
            return new WatchConfigurationPreferences[size];
        }
    };

    public double getLowTemp() {
        if (mBundle == null) {
            return DEFAULT_LOW_TEMP;
        }
        return mBundle.getDouble(WatchConfigurationConstants.KEY_LOW_TEMP, DEFAULT_LOW_TEMP);
    }

    public double getHighTemp() {
        if (mBundle == null) {
            return DEFAULT_HIGH_TEMP;
        }
        return mBundle.getDouble(WatchConfigurationConstants.KEY_HIGH_TEMP, DEFAULT_HIGH_TEMP);
    }

    public int getWeatherId() {
        if (mBundle == null) {
            return DEFAULT_WEATHER_INFO;
        }
        return mBundle.getInt(WatchConfigurationConstants.KEY_WEATHER_INFO, DEFAULT_WEATHER_INFO);
    }

    public static class Builder {

        private Bundle bundle;

        public Builder() {
            this.bundle = new Bundle();
        }

        public Builder addHighTemperature(double value) {
            this.bundle.putDouble(WatchConfigurationConstants.KEY_HIGH_TEMP, value);
            return this;
        }

        public Builder addLowTemperature(double value) {
            this.bundle.putDouble(WatchConfigurationConstants.KEY_LOW_TEMP, value);
            return this;
        }

        public Builder addWeatherInfoId(int resId) {
            this.bundle.putInt(WatchConfigurationConstants.KEY_WEATHER_INFO, resId);
            return this;
        }

        public WatchConfigurationPreferences build() {
            return new WatchConfigurationPreferences(bundle);
        }
    }

    public static class WatchConfigurationConstants {
        public static final String KEY_LOW_TEMP = NAME + ".KEY_LOW_TEMP";
        public static final String KEY_HIGH_TEMP = NAME + ".KEY_HIGH_TEMP";
        public static final String KEY_WEATHER_INFO = NAME + ".KEY_HIGH_TEMP";
    }

}
