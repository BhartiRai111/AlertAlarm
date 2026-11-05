package com.bharti.alertalarm;

import android.app.Application;

/** Loads theme and time format before any screen opens. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        TimeText.use24h = AppSettings.use24h(this);
        // Write crashes to the Alarm log so they can be seen in Settings
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            StackTraceElement[] st = e.getStackTrace();
            String where = st.length > 0 ? " at " + st[0].getClassName().replace("com.bharti.alertalarm.", "")
                    + "." + st[0].getMethodName() + ":" + st[0].getLineNumber() : "";
            AlarmLog.add(this, "💥 App crashed: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " (" + e.getMessage() + ")" : "") + where);
            if (previous != null) {
                previous.uncaughtException(thread, e);
            }
        });
        AppSettings.applyTheme(AppSettings.theme(this));
        Bedtime.schedule(this);
    }
}
