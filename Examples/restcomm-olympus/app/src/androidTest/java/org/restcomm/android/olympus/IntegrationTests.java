package org.restcomm.android.olympus;


import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.rule.ServiceTestRule;
import android.support.test.rule.UiThreadTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.restcomm.android.sdk.util.RCException;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.AllOf.allOf;

import static org.awaitility.Awaitility.*;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class IntegrationTests implements RCDeviceListener {
    private static final String TAG = "IntegrationTests";
    private boolean initialized = false;
    private Handler testHandler;

    @Rule
    public UiThreadTestRule uiThreadTestRule = new UiThreadTestRule();

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();
    //public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(60L, TimeUnit.SECONDS);

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
        // Some background on why Looper.prepare() is needed here:
        // - If you try to use ServiceTestRule you will get "Can't create handler inside thread that has not called Looper.prepare()". Remember that by default the thread were TCs are ran is not
        //   a UI thread where a Looper is prepared and ran properly, hence we need to run it ourselves. Notice that we must not call Looper.loop() as it will be called later by our Signaling facilities
        //   when the Handler to receive incoming messages from the Signaling thread is created.
        // - Another way I tried is to run the TC in the UI thread by using @UiThreadTest. But that failed because it wouldn't play nicely with ServiceTestRule, which would timeout. I guess the design
        //   is such internally that this isn't allowed
        Looper.prepare();

        /*
        new Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    @Override
                    public void run()
                    {
                        try {
                            IBinder binder = mServiceRule.bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class));

                            //bindService(new Intent(this, RCDevice.class), this, Context.BIND_AUTO_CREATE);

                            // Get the reference to the service, or you can call
                            // public methods on the binder directly.
                            RCDevice device = ((RCDevice.RCDeviceBinder) binder).getService();


                            HashMap<String, Object> params = new HashMap<String, Object>();
                            params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                            params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                            params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "192.168.2.3:5080");
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

                            try {
                                device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                            } catch (RCException e) {
                                Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
                            }

                            //Looper.loop();
                            //Looper.getMainLooper().quit();

                            // Verify that the service is working correctly.
                            //assertThat(service.getRandomInt(), is(any(Integer.class)));

                            Log.i(TAG, "Before wait");
                            await().atMost(15, TimeUnit.SECONDS).until(deviceOnInitialized());
                            Log.i(TAG, "After wait");
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        }
                    }
                }
                , 1);

        Looper.loop();
        */

        // Create the service Intent.
        //Intent serviceIntent = new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class);


        // Data can be passed to the service via the Intent.
        //serviceIntent.putExtra(LocalService.SEED_KEY, 42L);

        //IBinder binder = mServiceRule.bindService(new Intent(InstrumentationRegistry.getTargetContext(), MyService.class));
        //MyService service = ((MyService.LocalBinder) binder).getService();
        //assertTrue("True wasn't returned", service.doSomethingToReturnTrue());

        // Bind the service and grab a reference to the binder
        IBinder binder = mServiceRule.bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class));
        //bindService(new Intent(this, RCDevice.class), this, Context.BIND_AUTO_CREATE);

        // Get the reference to the service, or you can call
        // public methods on the binder directly.
        RCDevice device = ((RCDevice.RCDeviceBinder) binder).getService();

        HashMap<String, Object> params = new HashMap<String, Object>();
        params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
        params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
        params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "192.168.2.3:5080");
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

        try {
            device.initialize(InstrumentationRegistry.getTargetContext(), params, this);
        }
        catch (RCException e) {
            Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
        }

        testHandler = new Handler(Looper.myLooper());
        testHandler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        //initialized = true;
                        Log.i(TAG, "Before wait");
                        await().atMost(15, TimeUnit.SECONDS).until(deviceOnInitialized());
                        Log.i(TAG, "After wait");

                        try {
                            testHandler.getLooper().quit();
                        }
                        catch (IllegalStateException e) {
                            e.printStackTrace();
                        }
                    }
                }
                , 10000);

        Looper.loop();

        // Verify that the service is working correctly.
        //assertThat(service.getRandomInt(), is(any(Integer.class)));
    }

    private Callable<Boolean> deviceOnInitialized() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return initialized; // The condition that must be fulfilled
            }
        };
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
        initialized = true;
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
