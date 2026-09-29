package org.androshogi;

import org.androshogi.settings.AppSettings;

import android.app.Application;

public class AndroShogiApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Night mode has to be set before the first activity inflates its theme.
        AppSettings.applyTheme(this);
    }
}
