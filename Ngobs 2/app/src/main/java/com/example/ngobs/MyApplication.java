package com.example.ngobs; // Make sure this matches your package name

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory; // <-- NEW IMPORT

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        // 1. Check if the app is running in a DEBUG build (emulator or USB-connected device)
        if (BuildConfig.DEBUG) {
            // Use the Debug Provider: This will print a unique debug token to Logcat
            // that you MUST register in the Firebase Console (App Check > Manage debug tokens).
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
            );
        } else {
            // Use the Play Integrity Provider for RELEASE builds
            // This requires your SHA-256 fingerprint to be registered in Firebase.
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
            );
        }
    }
}