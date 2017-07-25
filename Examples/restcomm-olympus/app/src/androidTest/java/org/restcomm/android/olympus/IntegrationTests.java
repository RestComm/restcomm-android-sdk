package org.restcomm.android.olympus;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCConnectionListener;
import org.restcomm.android.sdk.RCDevice;
import org.restcomm.android.sdk.RCDeviceListener;

import org.restcomm.android.sdk.util.PercentFrameLayout;
import org.restcomm.android.sdk.util.RCException;
import org.webrtc.SurfaceViewRenderer;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.AllOf.allOf;

import static org.awaitility.Awaitility.*;
import static org.assertj.core.api.Assertions.*;


import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
/**
 * This class implements instrumented (i.e. on Device) integration tests facilities for the Restcomm Android SDK using JUnit4. Instrumented tests
 * are more complex and more time consuming, but they need to be instrumented if we are to get realistic results. Integration tests are not meant to
 * replace UI or Unit tests, but to complement them by providing a way to really test our SDK's API top to bottom.
 *
 * The idea is that it exercises the high level API (i.e. comprised by RCDevice, RCConnection, etc entities) and to do that
 * it implements RCDevice/RCConnection callbacks, as well as ServiceConnection callbacks, since the SDK is wrapped into an Android Service.
 *
 * Since the high level API is asynchronous we are using Awaitility framework to wait for and test conditions of the SDK's callbacks. For
 * example to test RCDevice initialize functionality we call RCDevice.initialize() and then tell Awaitility to wait until the 'onInitialized'
 * callback fires and when it does verify that all is good
 *
 * Design and synchronization considerations
 *
 * The IT facilities are desined so that there are 3 threads involved in the tests (separate media threads are not relevant to this so they are kept out):
 * - The testing thread, where test cases run
 * - The API/Service thread where RCDevice/RCConnection calls are made, and relative callbacks are invoked
 * - The Signaling thread that runs JAIN SIP user code and stack
 *
 * The reason we are using this setup is so that the testing thread that runs Awaitility framework (that provides facilities to wait
 * on various conditions) needs to be on a separate thread than API thread otherwise when API thread is blocked waiting for messages from Signaling thread
 * the Awaitility framework wouldn't have a way to wait on conditions properly and the whole thing would break. Also, we want the testing thread to
 * be separate so that it's not affected by any timing issues on ANRs that the API thread might encounter. Finally we need to be able to write our tests
 * linearly so that they are easy to read and understand. So even though we could potentially keep Awaitility out and just use a single thread for testing and API
 * by adding more logic in the callback, the whole thing would become a mess as we'd have to keep state all over the place
 *
 * TODO: describe how Handlers & HandlerThreads are used, and why. Also describe how to avoid threading issues by always using the Main Looper for API thread
 *
 * Extending the Integration Tests
 *
 * TODO: describe the assumptions made about the tests, like conditions variables and context, as well as synchronization between test & API threads that is not really implemented
 *
 * Additional notes
 *
 * TODO: describe the issues with ServiceTestRule and why we removed it
 *
 */
public class IntegrationTests implements RCDeviceListener, RCConnectionListener, ServiceConnection {
    private static final String TAG = "IntegrationTests";

    HashMap<String, Object> context = new HashMap<>();
    // test case timeout in miliseconds
    static private final int TC_TIMEOUT = 30000;
    // timeout for signaling actions in seconds
    static private final int SIGNALING_TIMEOUT = 10;
    // special timeout for a call getting connected in seconds. We use separate timeout for this as we want to enforce separately very low setup times.
    // Starting with 10 seconds, but goal it to not take more than 1-3 seconds
    static private final int CALL_CONNECTED_TIMEOUT = 10;
    // how long to keep the call ongoing before hanging up in miliseconds
    static private final int CALL_DURATION = 5000;

    // Condition variables. Even though its a bit messy I'm keeping different for each state, to make sure we 're not messing the states
    private boolean deviceInitialized, deviceReleased, serviceConnected, connectionConnected, connectionDisconnected, deviceStartedListening,
            deviceStoppedListening, deviceConnectivityUpdated, deviceMessageSent, connectionConnecting, connectionDigitSent, connectionCancelled,
            connectionDeclined, connectionError, connectionLocalVideo, connectionRemoteVideo;

    RCDevice.RCDeviceBinder binder;

    //@Rule
    //public UiThreadTestRule uiThreadTestRule = new UiThreadTestRule();

    //@Rule
    //public final ServiceTestRule mServiceRule = new ServiceTestRule();

    /**
     * Test Cases
     */
    @Before
    public void initialize()
    {
        deviceInitialized = false;
        deviceReleased = false;
        serviceConnected = false;
        connectionConnected = false;
        connectionDisconnected = false;
        deviceStartedListening = false;
        deviceStoppedListening = false;
        deviceConnectivityUpdated = false;
        deviceMessageSent = false;
        connectionConnecting = false;
        connectionDigitSent = false;
        connectionCancelled = false;
        connectionDeclined = false;
        connectionError = false;
        connectionLocalVideo = false;
        connectionRemoteVideo = false;
    }

    /*
    @After
    public void afterAction() {
    }
    */

    // Test initializing RCDevice with proper credentials and cleartext signaling
    //@UiThreadTest
    @Test(timeout = TC_TIMEOUT)
    public void deviceInitialize_Valid() throws InterruptedException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        HandlerThread clientHandlerThread = new HandlerThread("client-thread");
        clientHandlerThread.start();
        //Handler clientHandler = new Handler(clientHandlerThread.getLooper());
        Handler clientHandler = new Handler(Looper.getMainLooper());

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

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.READY);
        assertThat(context.get("status-code")).isEqualTo(0);

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        clientHandlerThread.quit();
        //assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    // Test initializing RCDevice with proper credentials and encrypted signaling
    //@UiThreadTest
    @Test(timeout = TC_TIMEOUT)
    public void deviceInitialize_EncryptedSignaling_Valid() throws InterruptedException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        HandlerThread clientHandlerThread = new HandlerThread("client-thread");
        clientHandlerThread.start();
        //Handler clientHandler = new Handler(clientHandlerThread.getLooper());
        Handler clientHandler = new Handler(Looper.getMainLooper());

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


        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.READY);
        assertThat(context.get("status-code")).isEqualTo(0);


        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);


        clientHandlerThread.quit();
        //assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    // Test making an audio call after initializing RCDevice
    //@UiThreadTest
    @Test(timeout = TC_TIMEOUT)
    public void deviceMakeAudioCall_Valid() throws InterruptedException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        HandlerThread clientHandlerThread = new HandlerThread("client-thread");
        clientHandlerThread.start();
        //Handler clientHandler = new Handler(clientHandlerThread.getLooper());
        Handler clientHandler = new Handler(Looper.getMainLooper());

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
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.READY);
        assertThat(context.get("status-code")).isEqualTo(0);

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> connectParams = new HashMap<String, Object>();
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "+1235");
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, false);

                try {
                    RCConnection connection = device.connect(connectParams, IntegrationTests.this);
                } catch (RCException e) {
                    e.printStackTrace();
                }
            }
        });
        await().atMost(CALL_CONNECTED_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("connectionConnected"), equalTo(true));
        assertThat(((RCConnection)context.get("connection")).getState()).isEqualTo(RCConnection.ConnectionState.CONNECTED);
        assertThat(((RCConnection)context.get("connection")).getLocalMediaType()).isEqualTo(RCConnection.ConnectionMediaType.AUDIO);
        assertThat(((RCConnection)context.get("connection")).getRemoteMediaType()).isEqualTo(RCConnection.ConnectionMediaType.AUDIO);
        assertThat(((RCConnection)context.get("connection")).isIncoming()).isEqualTo(false);

        // Need to just wait for some time in the call before we hang up to make scenario more realistic and have actual media stats gathered
        Thread.sleep(CALL_DURATION);

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                RCConnection connection = device.getLiveConnection();
                connection.disconnect();
            }
        });
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("connectionDisconnected"), equalTo(true));
        assertThat(((RCConnection)context.get("connection")).getState()).isEqualTo(RCConnection.ConnectionState.DISCONNECTED);

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        clientHandlerThread.quit();
        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    // Test making a video call after initializing RCDevice
    //@UiThreadTest
    @Test(timeout = TC_TIMEOUT)
    public void deviceMakeVideoCall_Valid() throws InterruptedException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        HandlerThread clientHandlerThread = new HandlerThread("client-thread");
        clientHandlerThread.start();
        //Handler clientHandler = new Handler(clientHandlerThread.getLooper());
        Handler clientHandler = new Handler(Looper.getMainLooper());

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
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.READY);
        assertThat(context.get("status-code")).isEqualTo(0);

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                PercentFrameLayout localRenderLayout = new PercentFrameLayout(InstrumentationRegistry.getTargetContext());
                SurfaceViewRenderer localSurfaceViewRenderer = new SurfaceViewRenderer(InstrumentationRegistry.getTargetContext());
                localRenderLayout.addView(localSurfaceViewRenderer);

                PercentFrameLayout remoteRenderLayout = new PercentFrameLayout(InstrumentationRegistry.getTargetContext());
                SurfaceViewRenderer remoteSurfaceViewRenderer = new SurfaceViewRenderer(InstrumentationRegistry.getTargetContext());
                remoteRenderLayout.addView(remoteSurfaceViewRenderer);

                HashMap<String, Object> connectParams = new HashMap<String, Object>();
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "+1235");
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, localRenderLayout);
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, remoteRenderLayout);

                try {
                    RCConnection connection = device.connect(connectParams, IntegrationTests.this);
                } catch (RCException e) {
                    e.printStackTrace();
                }
            }
        });
        await().atMost(CALL_CONNECTED_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("connectionConnected"), equalTo(true));
        // Need to just wait for some time in the call before we hang up to make scenario more realistic and have actual media stats gathered
        Thread.sleep(CALL_DURATION);

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                RCConnection connection = device.getLiveConnection();
                connection.disconnect();
            }
        });
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("connectionDisconnected"), equalTo(true));

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);


        clientHandlerThread.quit();
        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void devicedMakeRegistralessCall_Valid() throws InterruptedException
    {
    }

    /**
     * Awaitility condition methods. Not using for now as we 're using fields directly
     */
    /*
    private Callable<Boolean> deviceOnInitialized() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return deviceInitialized; // The condition that must be fulfilled
            }
        };
    }
    private Callable<Boolean> deviceOnReleased() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return deviceReleased; // The condition that must be fulfilled
            }
        };
    }
    private Callable<Boolean> serviceOnConnected() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return serviceConnected; // The condition that must be fulfilled
            }
        };
    }
    private Callable<Boolean> serviceOnDisconnected() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return serviceDisconnected; // The condition that must be fulfilled
            }
        };
    }
    */


    /**
     * RCDeviceListener callbacks
     */
    public void onStartListening(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus)
    {
        Log.i(TAG, "%% onStartListening");

        context.clear();
        context.put("device", device);
        context.put("connectivity-status", connectivityStatus);

        deviceStartedListening = true;
    }

    public void onStopListening(RCDevice device, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onStopListening");

        context.clear();
        context.put("device", device);
        context.put("status-code", statusCode);

        deviceStoppedListening = true;
    }

    public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onInitialized");

        context.clear();
        context.put("device", device);
        context.put("connectivity-status", connectivityStatus);
        context.put("status-code", statusCode);

        deviceInitialized = true;
    }
    public void onReleased(RCDevice device, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onReleased");
        context.clear();
        context.put("device", device);
        context.put("status-code", statusCode);

        deviceReleased = true;
    }
    public void onConnectivityUpdate(RCDevice device, RCConnectivityStatus connectivityStatus)
    {
        Log.i(TAG, "%% onConnectivityUpdate");

        context.clear();
        context.put("device", device);
        context.put("connectivity-status", connectivityStatus);

        deviceConnectivityUpdated = true;
    }
    public void onMessageSent(RCDevice device, int statusCode, String statusText, String jobId)
    {
        Log.i(TAG, "%% onMessageSent");

        context.clear();
        context.put("device", device);
        context.put("status-code", statusCode);

        deviceMessageSent = true;
    }

    /**
     * RCConnectionListener callbacks
     */
    @Override
    public void onConnecting(RCConnection connection) {
        Log.i(TAG, "%% onConnecting");

        context.clear();
        context.put("connection", connection);

        connectionConnecting = true;
    }

    @Override
    public void onConnected(RCConnection connection, HashMap<String, String> customHeaders) {
        Log.i(TAG, "%% onConnected");

        context.clear();
        context.put("connection", connection);

        connectionConnected = true;
    }

    @Override
    public void onDisconnected(RCConnection connection) {
        Log.i(TAG, "%% onDisconnected");

        context.clear();
        context.put("connection", connection);

        connectionDisconnected = true;
    }

    @Override
    public void onDigitSent(RCConnection connection, int statusCode, String statusText) {
        Log.i(TAG, "%% onDigitSent");

        context.clear();
        context.put("connection", connection);
        context.put("status-code", statusCode);

        connectionDigitSent = true;
    }

    @Override
    public void onCancelled(RCConnection connection) {
        Log.i(TAG, "%% onCancelled");

        context.clear();
        context.put("connection", connection);

        connectionCancelled = true;
    }

    @Override
    public void onDeclined(RCConnection connection) {
        Log.i(TAG, "%% onDeclined");

        context.clear();
        context.put("connection", connection);

        connectionDeclined = true;
    }

    @Override
    public void onError(RCConnection connection, int errorCode, String errorText) {
        Log.i(TAG, "%% onError");

        context.clear();
        context.put("connection", connection);
        context.put("error-code", errorCode);

        connectionError = true;
    }

    @Override
    public void onLocalVideo(RCConnection connection) {
        Log.i(TAG, "%% onLocalVideo");

        context.clear();
        context.put("connection", connection);

        connectionLocalVideo = true;
    }

    @Override
    public void onRemoteVideo(RCConnection connection) {
        Log.i(TAG, "%% onRemoteVideo");

        context.clear();
        context.put("connection", connection);

        connectionRemoteVideo = true;
    }


    /**
     * Service callbacks
     */
    @Override
    public void onServiceConnected(ComponentName name, IBinder service)
    {
        Log.i(TAG, "%% onServiceConnected");
        binder = (RCDevice.RCDeviceBinder) service;

        serviceConnected = true;
    }

    // Notice that this isn't really called as a result of unbind, so it's not really usable for testing
    @Override
    public void onServiceDisconnected(ComponentName name)
    {
        Log.i(TAG, "%% onServiceDisconnected");
    }

}
