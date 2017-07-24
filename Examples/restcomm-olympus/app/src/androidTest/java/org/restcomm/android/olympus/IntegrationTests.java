package org.restcomm.android.olympus;


import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.rule.UiThreadTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
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

import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.AllOf.allOf;

import static org.awaitility.Awaitility.*;
import static org.assertj.core.api.Assertions.*;
//import static org.assertj.android.api.Assertions.assertThat;


import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class IntegrationTests implements RCDeviceListener {
    private static final String TAG = "IntegrationTests";
    //private Handler testHandler;
    static private final int TIMEOUT = 120000;

    // Condition variables
    private boolean initialized;  // = false;
    private boolean released;  // = false;


    //@Rule
    //public UiThreadTestRule uiThreadTestRule = new UiThreadTestRule();

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();
    //public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(60L, TimeUnit.SECONDS);


    @Before
    public void initialize()
    {
        initialized = false;
        released = false;
    }


    /*
    @After
    public void afterAction() {
    }
    */


    @Test(timeout = TIMEOUT)
    // Test initializing RCDevice with proper credentials and cleartext signaling
    public void deviceInitialize_Valid() throws TimeoutException
    {
        Log.i(TAG, "------------------------------------------------------------");
        IBinder binder = mServiceRule.bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = ((RCDevice.RCDeviceBinder) binder).getService();

        HandlerThread clientHandlerThread = new HandlerThread("client-thread");
        clientHandlerThread.start();
        Handler clientHandler = new Handler(clientHandlerThread.getLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
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
                params.put(RCDevice.ParameterKeys.DEBUG_JAIN_DISABLE_CERTIFICATE_VERIFICATION, true);

                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
                }
            }
        });

        //Log.i(TAG, "Before wait");
        await().atMost(10, TimeUnit.SECONDS).until(deviceOnInitialized());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(10, TimeUnit.SECONDS).until(deviceOnReleased());

        clientHandlerThread.quit();


        //assertThat(released).isTrue();

        Log.i(TAG, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
    }


    @Test(timeout = TIMEOUT)
    // Test initializing RCDevice with proper credentials and cleartext signaling
    public void deviceInitialize_EncryptedSignaling_Valid() throws TimeoutException
    {
/*
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
*/
        Log.i(TAG, "----------------------------------------------------------");
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class);
        IBinder binder = mServiceRule.bindService(intent);

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = ((RCDevice.RCDeviceBinder) binder).getService();

        HandlerThread clientHandlerThread = new HandlerThread("client-thread-2");
        clientHandlerThread.start();
        Handler clientHandler = new Handler(clientHandlerThread.getLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "192.168.2.3:5081");
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "bob");
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, "1234");
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "https://service.xirsys.com/ice");
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, "atsakiridis");
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, "4e89a09e-bf6f-11e5-a15c-69ffdcc2b8a7");
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, "cloud.restcomm.com");
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, true);
                params.put(RCDevice.ParameterKeys.DEBUG_JAIN_DISABLE_CERTIFICATE_VERIFICATION, true);

                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
                }
            }
        });

        //Log.i(TAG, "Before wait");
        await().atMost(10, TimeUnit.SECONDS).until(deviceOnInitialized());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(10, TimeUnit.SECONDS).until(deviceOnReleased());

        clientHandlerThread.quit();
        //assertThat(released).isTrue();
        Log.i(TAG, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
    }


    /**
     * Awaitility condition methods
     */
    private Callable<Boolean> deviceOnInitialized() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return initialized; // The condition that must be fulfilled
            }
        };
    }

    private Callable<Boolean> deviceOnReleased() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return released; // The condition that must be fulfilled
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
        if (statusCode == 0) {
            initialized = true;
        }
    }

    public void onReleased(RCDevice device, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onReleased");
        if (statusCode == 0) {
            released = true;
        }
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
