package com.ant.sunshine.app.application;

import android.app.Application;

/**
 * Created by andrei on 4/14/16.
 */
public class SunshineApplication extends Application {

    private static SunshineApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        SunshineApplication.instance = this;
    }

    public static SunshineApplication getInstance() {
        return instance;
    }
}
