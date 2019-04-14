package net.evendanan.lumiere;

import android.app.Application;
import android.os.StrictMode;
import androidx.appcompat.app.AppCompatDelegate;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

public class LumiereApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
        //this is required since Android VM will crash the app if we use file:// URI.
        //But, since the Pebble app was built with support for file:// URIs....
        //the other way to do it would be to use FileProvider, but the Pebble app is only
        //allowing content:// from well-known providers (gmail, Inbox, downloads and a few others)
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
    }
}
