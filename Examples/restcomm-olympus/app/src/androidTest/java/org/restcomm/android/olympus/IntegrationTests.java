package org.restcomm.android.olympus;


import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.ViewInteraction;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.internal.util.Checks;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.v4.content.ContextCompat;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;

import static android.support.test.espresso.Espresso.onData;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.AllOf.allOf;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class IntegrationTests implements RCDeviceListener {
    private static final String TAG = "IntegrationTests";
    final String smsText = "Hello there";

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    /*
    @Before
    public void signinIfNeeded()
    {
    }
    */

    /*
    @After
    public void afterAction() {
    }
    */


    @Test
    // Test on calling a Restcomm number, using 1235
    public void integrationTest1() throws TimeoutException
    {
        // Create the service Intent.
        Intent serviceIntent =
                new Intent(InstrumentationRegistry.getTargetContext(),
                        RCDevice.class);

        // Data can be passed to the service via the Intent.
        //serviceIntent.putExtra(LocalService.SEED_KEY, 42L);

        // Bind the service and grab a reference to the binder.
        IBinder binder = mServiceRule.bindService(serviceIntent);

        // Get the reference to the service, or you can call
        // public methods on the binder directly.
        RCDevice device = ((RCDevice.RCDeviceBinder) binder).getService();

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
        params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
        params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "192.168.2.35:5080");
        params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "bob");
        params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234");
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "https://service.xirsys.com/ice");
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, "atsakiridis");
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, "4e89a09e-bf6f-11e5-a15c-69ffdcc2b8a7");
        params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, "cloud.restcomm.com");
        params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
        params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);

        // The SDK provides the user with default sounds for calling, ringing, busy (declined) and message, but the user can override them
        // by providing their own resource files (i.e. .wav, .mp3, etc) at res/raw passing them with Resource IDs like R.raw.user_provided_calling_sound
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_CALLING, R.raw.user_provided_calling_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_RINGING, R.raw.user_provided_ringing_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_DECLINED, R.raw.user_provided_declined_sound);
        //params.put(RCDevice.ParameterKeys.RESOURCE_SOUND_MESSAGE, R.raw.user_provided_message_sound);

        // WARNING: These are for debugging purposes, NOT for release builds!
        // This is handy when connecting to a testing/staging Restcomm Connect instance that typically has a self-signed certificate which is not acceptable by the client by default.
        // With this setting we override that behavior to accept it. NOT for production!
        params.put(RCDevice.ParameterKeys.DEBUG_JAIN_DISABLE_CERTIFICATE_VERIFICATION, true);
        //params.put(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED, prefs.getBoolean(RCDevice.ParameterKeys.DEBUG_JAIN_SIP_LOGGING_ENABLED, true));

        device.setLogLevel(Log.VERBOSE);
        // Verify that the service is working correctly.
        //assertThat(service.getRandomInt(), is(any(Integer.class)));
    }


    /**
     * RCDeviceListener callbacks
     */
    public void onStartListening(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus)
    {
        Log.i(TAG, "%% onStartListening");
    }

    public void onStopListening(RCDevice device, int errorCode, String errorText)
    {
        Log.i(TAG, "%% onStopListening");
    }

    public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onInitialized");
    }

    public void onReleased(RCDevice device, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onReleased");

    }

    public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
    {
        Log.i(TAG, "%% onConnectivityUpdate");

    }

    public void onMessageSent(RCDevice device, int statusCode, String statusText, String jobId)
    {
        Log.i(TAG, "%% onMessageSent");
    }
}
