package com.bharti.alertalarm;

import android.app.Application;

/** Loads theme and time format before any screen opens. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        TimeText.use24h = AppSettings.use24h(this);
        AppSettings.applyTheme(AppSettings.theme(this));
    }
}
