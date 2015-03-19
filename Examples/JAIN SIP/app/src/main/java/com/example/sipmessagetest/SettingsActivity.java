package com.example.sipmessagetest;
import android.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;


public class SettingsActivity extends PreferenceActivity {
	 @SuppressWarnings("deprecation")
	@Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	       
	       addPreferencesFromResource(com.example.sipmessagetest.R.xml.preference);
	 
	    }
}
