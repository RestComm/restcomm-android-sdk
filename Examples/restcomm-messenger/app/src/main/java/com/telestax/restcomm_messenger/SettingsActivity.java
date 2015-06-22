package com.telestax.restcomm_messenger;

import android.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;


public class SettingsActivity extends PreferenceActivity {
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(com.telestax.restcomm_messenger.R.xml.preferences);
    }
}

