package com.telestax.restcommmessenger;

import android.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.v7.app.ActionBar;


public class SettingsActivity extends AppCompatPreferenceActivity {
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupActionBar();
        //getActionBar().setDisplayHomeAsUpEnabled(true);

        // TODO: this is deprecated. The preferred way to go is to use preference fragment, as described at http://developer.android.com/guide/topics/ui/settings.html#Fragment
        addPreferencesFromResource(com.telestax.restcommmessenger.R.xml.preferences);
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
}

