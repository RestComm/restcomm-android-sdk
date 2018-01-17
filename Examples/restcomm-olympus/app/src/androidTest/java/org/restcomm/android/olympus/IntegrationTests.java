package org.restcomm.android.olympus;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.ResponseHandlerInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.restcomm.android.sdk.RCClient;
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


import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;


import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpResponse;

@RunWith(AndroidJUnit4.class)
/**
 * This class implements instrumented integration test facilities (i.e. that run on device) for the Restcomm Android SDK using JUnit4. Instrumented tests
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
 * - The testing thread, where test cases run; this is the default thread where you test code runs unless told otherwise
 * - The API/Service thread where RCDevice/RCConnection calls are made, and relative callbacks are invoked (i.e. RCDevice.onInitialized())
 * - The Signaling thread that runs JAIN SIP user code and stack
 *
 * The reason we are using this setup is so that the testing thread that runs Awaitility framework (that provides facilities to wait
 * on various conditions) needs to be on a separate thread than API/Service thread otherwise when API thread is blocked waiting for messages from Signaling thread
 * the Awaitility framework wouldn't have a way to wait on conditions properly and the whole thing would break. Also, we want the testing thread to
 * be separate so that it's not affected by any timing issues or ANRs that the API/Service thread might encounter. Finally we need to be able to write our tests
 * linearly so that they are easy to read and extend. So even though we could potentially keep Awaitility out and just use a single thread for testing and API
 * by adding more logic in the callback, the whole thing would become a mess as we'd have to keep state all over the place
 *
 * Adding/Extending an Integration Test
 *
 * Each integration test needs to do the following (please check deviceInitialize_Valid() test case for more details):
 * - Bind to the RCDevice Android Service and wait until it is connected using Awaitility condition variable (i.e. serviceConnected; there is a separate condition variable for each
 *   API/Service callback we are interested to keep things clear). This happens in the current (i.e. testing) thread
 * - Create an Android Handler and associate it with the Main Looper thread so that you can post work for it easily and linearly. Important: the Main Looper thread
 *   is by default SEPARATE from the test thread, and the reason we use this Main Looper Thread specifically (instead of for example creating a new HandlerThread) is that inside
 *   the API/Service this is the thread we explicitly use in some occasions to refer to the Service thread, so we need to stay consistent. For example the first thing
 *   you will want to do in a test case is call RCDevice.initialize(). This needs to happen inside the API/Service thread
 * - Wait for RCDevice to be initialized on the testing thread. To do this you need to wait on an Awaitility condition variable (i.e. deviceInitialized) which is set when
 *   RCDevice.onInitialized() is called on the API/Service thread.
 * - Assert that all is good by inspecting the test context. Context is a HashMap that gets filled from the API/Service thread when a response is received with any information
 *   that we might need like RCDevice object or status code
 * - After that you can continue posting actions in the API/Service thread, waiting for responses in the testing thread and asserting results sequentially.
 *
 * Note that in the future if needed, we could potentially use another thread for API/Service instead of Main Looper thread. But if we do that we need to make sure to fix code
 * inside API/Service so that it doesn't use .getMainLooper() as it does right now. Otherwise we are bound to have synchronization issues.
 *
 * Notice that so far synchronization between test & API threads is implicit; we 're not using any Java synchronization constructs. So far it hasn't caused us any issues
 * in the sense that context is not read by the test thread until condition variable (not to be confused with synchronization condition variables) is set by the API/Service
 * thread, which means that API/Service thread has finished writing it. Also the context variable is emptied and re-set from the API/Service thread whenever a response arrives.
 * Still the condition variable is accessed by both API/Service thread and test thread, so we might need
 * to remedy that at some point.
 *
 * Additional notes
 *
 * Originally we used a ServiceTestRule to help us in waiting until the Android Service is ready, but we encountered some issues so we fell back to using Awaitility since it provides
 * pretty nice facilities on waiting for condition.
 *
 */
public class IntegrationTests extends BroadcastReceiver implements RCDeviceListener, RCConnectionListener, ServiceConnection {
    private static final String TAG = "IntegrationTests";

    private HashMap<String, Object> context = new HashMap<>();
    private HashMap<String, Object> preContext = new HashMap<>();
    // test case timeout in miliseconds
    static private final int TC_TIMEOUT = 30 * 1000;
    // timeout for signaling actions in seconds
    static private final int SIGNALING_TIMEOUT = 10;
    static private final int PUSH_TIMEOUT = 40;
    // special timeout for a call getting connected in seconds. We use separate timeout for this as we want to enforce separately very low setup times.
    // Starting with 10 seconds, but goal it to not take more than 1-3 seconds
    static private final int CALL_CONNECTED_TIMEOUT = 10;
    // how long to keep the call ongoing before hanging up in miliseconds
    static private final int CALL_DURATION = 5000;
    // timeout when waiting for REST calls
    static private final int REST_TIMEOUT = 10;

    // Various properties used in TCs
    //static private final String SERVER_HOST = "192.168.2.33";
    static private final String SERVER_HOST = "cloud.restcomm.com";
    static private final int SERVER_PORT = 5060;   // 5080
    static private final int SERVER_ENCRYPTED_PORT = 5061;   // 5081
    static private final String CLIENT_NAME = BuildConfig.TEST_RESTCOMM_LOGIN;   //"bob";
    static private final String CLIENT_PASSWORD = BuildConfig.TEST_RESTCOMM_PASSWORD;   //"1234";
    static private final String ICE_URL = "https://service.xirsys.com/ice";
    static private final String ICE_USERNAME = "atsakiridis";
    static private final String ICE_PASSWORD = "4e89a09e-bf6f-11e5-a15c-69ffdcc2b8a7";
    static private final String ICE_DOMAIN = "cloud.restcomm.com";
    static private final String PEER = "+1235";
    static private final String TEXT_MESSAGE = "Hello from Integration Tests";

    // who will show as 'From' in call or messages sent from Restcomm as a result of a REST request from Android client
    static private final String REST_ORIGINATOR = "alice";
    //static private final String REST_ENDPOINT = "192.168.2.33:8443";
    //static private final String REST_ENDPOINT = "cloud.restcomm.com";
    static private final String REST_PORT = "443";   // "8443";
    static private final String REST_RESTCOMM_ACCOUNT_SID = BuildConfig.TEST_RESTCOMM_ACCOUNT_SID;
    static private final String REST_RESTCOMM_AUTH_TOKEN = BuildConfig.TEST_RESTCOMM_AUTH_TOKEN;
    static private final String MESSAGE_TEXT = "Hello there for Android IT";


    static private final String CLOUD = BuildConfig.TEST_RESTCOMM_LOGIN;   //"bob";

    static private final String PUSH_DOMAIN = "push.restcomm.com";
    static private final String HTTP_DOMAIN = "cloud.restcomm.com";
    static private final String PUSH_ACCOUNT = BuildConfig.TEST_PUSH_ACCOUNT; //bob@telestax.com
    static private final String PUSH_PASSWORD = BuildConfig.TEST_PUSH_PASSWORD; //1234
    static private final String PUSH_APPLICATION_NAME = "test android";
    static private final String PUSH_FCM_KEY = BuildConfig.TEST_PUSH_FCM_KEY;



    // Condition variables. Even though its a bit messy I'm keeping different for each state, to make sure we 're not messing the states
    private boolean deviceInitialized, deviceReleased, serviceConnected, connectionConnected, connectionDisconnected, deviceStartedListening,
            deviceStoppedListening, deviceConnectivityUpdated, connectionConnecting, connectionDigitSent, connectionCancelled,
            connectionDeclined, connectionError, connectionLocalVideo, connectionRemoteVideo, messageAcked, connectionArrived,
            messageArrived, deviceReconfigured;

    private RCDevice.RCDeviceBinder binder;

    // Make sure that the 'dangerous' dynamic permissions we need are requested automatically before the tests. That way we don't need
    // to manually provide them through build scripts and 'adb shell pm grant com.package.myapp android.permission.<PERMISSION>', etc
    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO, Manifest.permission.USE_SIP,
            Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Rule
    public TestName name = new TestName();

    //@Rule
    //public UiThreadTestRule uiThreadTestRule = new UiThreadTestRule();

    //@Rule
    //public final ServiceTestRule mServiceRule = new ServiceTestRule();

/*
    IntegrationTests()
    {

    }
*/

    /**
     * Test Cases
     */
    @Before
    public void beforeAction()
    {
        deviceInitialized = false;
        deviceReleased = false;
        serviceConnected = false;
        connectionConnected = false;
        connectionDisconnected = false;
        deviceStartedListening = false;
        deviceStoppedListening = false;
        deviceConnectivityUpdated = false;
        connectionConnecting = false;
        connectionDigitSent = false;
        connectionCancelled = false;
        connectionDeclined = false;
        connectionError = false;
        connectionLocalVideo = false;
        connectionRemoteVideo = false;
        messageAcked = false;
        connectionArrived = false;
        messageArrived = false;
        deviceReconfigured = false;

        IntentFilter filter = new IntentFilter();
        filter.addAction(RCDevice.ACTION_INCOMING_CALL);
        filter.addAction(RCDevice.ACTION_INCOMING_MESSAGE);

        // register for connectivity related events
        InstrumentationRegistry.getTargetContext().registerReceiver(this, filter);  //, null, new Handler(Looper.myLooper()));
    }

    @After
    public void afterAction()
    {
        //InstrumentationRegistry.getTargetContext().unbindService(this);

        //Log.e(TAG, "TC Name: " + name.getMethodName());
        InstrumentationRegistry.getTargetContext().unregisterReceiver(this);
    }

    /**
     *
     *
     * General RCDevice initialize/release Integration Tests
     *
     *
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

        // Even though we don't use a HandlerThread now and use the Main Looper thread that is separate from the testing thread, let's keep this
        // code in case we want to change the threading logic later
        //HandlerThread clientHandlerThread = new HandlerThread("client-thread");
        //clientHandlerThread.start();
        //Handler clientHandler = new Handler(clientHandlerThread.getLooper());
        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

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

        // Even though we don't use a HandlerThread now and use the Main Looper thread that is separate from the testing thread, let's keep this
        // code in case we want to change the threading logic later
        //clientHandlerThread.quit();

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

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_ENCRYPTED_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, true);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

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

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    /**
     *
     *
     * Call Integration Tests
     *
     *
     */

    // Test making an audio call after initializing RCDevice
    //@UiThreadTest
    @Test(timeout = TC_TIMEOUT)
    public void deviceMakeAudioCall_Valid() throws InterruptedException, JSONException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

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
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, PEER);
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
        // Verify that media stats are healthy
        assertThat(verifyMediaStats(((RCConnection)context.get("connection")).getStats())).isEqualTo("success");

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    // Test making a video call after initializing RCDevice. Notice that when targeting Restcomm number (as oposed another webrtc client supporting video)
    // there is no incoming stream for video as Restcomm negates it, and generally it doesn't make much sense to assert any of the video streams. If, however,
    // we target a webrtc client (like web olympus) there are video streams in both sides that work fine, even though there are no UI elements actually rendered.
    // I did try this scenario (by manually accepting the video call from web olympus) and web olympus receives video properly from the Android camera, as well as
    // returns video towards Android device (i.e. WebRTC stats show up fine in both directions). Notice though that such P2P scenarios cannot be properly automated
    //@UiThreadTest
    @Test(timeout = TC_TIMEOUT)
    public void deviceMakeVideoCall_Valid() throws InterruptedException, JSONException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

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
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, PEER);
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, localRenderLayout);
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, remoteRenderLayout);

                try {
                    // notice that RCConnection is being referenced internally by RCDevice, so it doesn't get GCd until processing
                    // is over
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
        assertThat(((RCConnection)context.get("connection")).getState()).isEqualTo(RCConnection.ConnectionState.DISCONNECTED);
        // Verify that media stats are healthy
        assertThat(verifyMediaStats(((RCConnection)context.get("connection")).getStats())).isEqualTo("success");

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceMakeAudioCall_EncryptedSignaling_Valid() throws InterruptedException, JSONException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_ENCRYPTED_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, true);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

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
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, PEER);
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
        // Verify that media stats are healthy
        assertThat(verifyMediaStats(((RCConnection)context.get("connection")).getStats())).isEqualTo("success");


        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceMakeRegistrarlessCall_Valid() throws InterruptedException, JSONException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "");
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

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
                connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "sip:+1235@" + SERVER_HOST + ":" + SERVER_PORT);
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
        // Verify that media stats are healthy
        assertThat(verifyMediaStats(((RCConnection)context.get("connection")).getStats())).isEqualTo("success");


        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceReceiveAudioCall_Valid() throws InterruptedException, JSONException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), IntegrationTests.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), IntegrationTests.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS, true);

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

        // Now that we are registered, prepare and do REST request towards Restcomm, so that it initialized a call towards us
        RequestParams params = new RequestParams();
        params.put("From", REST_ORIGINATOR);
        params.put("To", "client:" + CLIENT_NAME);
        params.put("Url", "https://" + SERVER_HOST + ":" + REST_PORT + "/restcomm/demos/hello-world.xml");

        String url = "https://" + REST_RESTCOMM_ACCOUNT_SID + ":" + REST_RESTCOMM_AUTH_TOKEN +
                "@" + SERVER_HOST + ":" + REST_PORT + "/restcomm/2012-04-24/Accounts/" + REST_RESTCOMM_ACCOUNT_SID +
                "/Calls.json";

        doRestRequest(url, params);

        // wait for incoming call from Restcomm, triggered by REST call above
        await().atMost(REST_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("connectionArrived"), equalTo(true));

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> acceptParams = new HashMap<String, Object>();
                acceptParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, false);

                RCConnection pendingConnection = device.getPendingConnection();
                pendingConnection.setConnectionListener(IntegrationTests.this);
                pendingConnection.accept(acceptParams);
            }
        });
        await().atMost(CALL_CONNECTED_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("connectionConnected"), equalTo(true));
        assertThat(((RCConnection)context.get("connection")).getState()).isEqualTo(RCConnection.ConnectionState.CONNECTED);
        assertThat(((RCConnection)context.get("connection")).getLocalMediaType()).isEqualTo(RCConnection.ConnectionMediaType.AUDIO);
        assertThat(((RCConnection)context.get("connection")).getRemoteMediaType()).isEqualTo(RCConnection.ConnectionMediaType.AUDIO);
        assertThat(((RCConnection)context.get("connection")).isIncoming()).isEqualTo(true);

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
        // Verify that media stats are healthy
        assertThat(verifyMediaStats(((RCConnection)context.get("connection")).getStats())).isEqualTo("success");

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    /**
     *
     *
     * Text message Integration Tests
     *
     *
     */
    @Test(timeout = TC_TIMEOUT)
    public void deviceSendMessage_Valid() throws InterruptedException, JSONException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

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
                HashMap<String, String> sendParams = new HashMap<String, String>();
                sendParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, PEER);
                try {
                    String jobId = device.sendMessage(TEXT_MESSAGE, sendParams);
                    preContext.put("job-id", jobId);
                } catch (RCException e) {
                    e.printStackTrace();
                }
            }
        });
        await().atMost(CALL_CONNECTED_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("messageAcked"), equalTo(true));
        assertThat(context.get("status-code")).isEqualTo(0);
        assertThat(context.get("job-id")).isEqualTo(preContext.get("job-id"));

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceReceiveMessage_Valid() throws InterruptedException, JSONException
    {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), IntegrationTests.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), IntegrationTests.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);
                params.put(RCDevice.ParameterKeys.DEBUG_USE_BROADCASTS_FOR_EVENTS, true);

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

        // Now that we are registered, prepare and do REST request towards Restcomm, so that it initialized a call towards us
        RequestParams params = new RequestParams();
        params.put("From", REST_ORIGINATOR);
        params.put("To", "client:" + CLIENT_NAME);
        params.put("Url", "https://" + SERVER_HOST + ":" + REST_PORT + "/restcomm/demos/hello-world.xml");
        params.put("Body", MESSAGE_TEXT);
        String url = "https://" + REST_RESTCOMM_ACCOUNT_SID + ":" + REST_RESTCOMM_AUTH_TOKEN +
                "@" + SERVER_HOST + ":" + REST_PORT + "/restcomm/2012-04-24/Accounts/" + REST_RESTCOMM_ACCOUNT_SID +
                "/SMS/Messages";

        doRestRequest(url, params);

        // wait for incoming call from Restcomm, triggered by REST call above
        await().atMost(REST_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("messageArrived"), equalTo(true));
        //assertThat(((String)context.get("message-peer"))).isEqualTo("");
        assertThat(((String)context.get("message-text"))).isEqualTo(MESSAGE_TEXT);

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        assertThat(((RCDevice)context.get("device")).getState()).isEqualTo(RCDevice.DeviceState.OFFLINE);
        assertThat(context.get("status-code")).isEqualTo(0);

        assertThat(deviceReleased).isTrue();

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }


    /**
     *
     *
     * Push notification Integration Tests
     *
     *
     *
     */
    @Test(timeout = TC_TIMEOUT)
    public void deviceInitializeWithPush_FCMKeyMissing() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "");

                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    //error is fired synchronous
                    assertThat(e.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_FCM_SERVER_KEY_MISSING);
                }
            }
        });

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceInitializeWithPush_AppNameMissing() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, "");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "server key");


                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    //error is fired synchronous
                    assertThat(e.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_NAME_MISSING);
                }
            }
        });

        InstrumentationRegistry.getTargetContext().unbindService(this);

    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceInitializeWithPush_InvalidAccountAndPassword() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, PUSH_APPLICATION_NAME);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "invalid_email@test.com");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "12345");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, PUSH_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, HTTP_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "server key");


                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    //error is fired synchronous
                    assertThat(e.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_AUTHENTICATION_FORBIDDEN);
                }
            }
        });

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceInitializeWithPush_InvalidPushDomain() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, PUSH_APPLICATION_NAME);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, PUSH_ACCOUNT);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, PUSH_PASSWORD);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "invalid domain");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, HTTP_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "server key");


                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    //error is fired synchronous
                    assertThat(e.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_INVALID_PUSH_DOMAIN);
                }
            }
        });

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceInitializeWithPush_InvalidHTTPDomain() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                HashMap<String, Object> params = new HashMap<String, Object>();
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, PUSH_APPLICATION_NAME);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, PUSH_ACCOUNT);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, PUSH_PASSWORD);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, PUSH_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, "http domain");
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "server key");


                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    assertThat(e.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_INVALID_HTTP_DOMAIN);
                }
            }
        });

        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceInitializeWithPush_DisableEnablePush_Valid() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());
        final HashMap<String, Object> params = new HashMap<String, Object>();

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, PUSH_APPLICATION_NAME);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, PUSH_ACCOUNT);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, PUSH_PASSWORD);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, PUSH_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, HTTP_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, PUSH_FCM_KEY);


                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
                }
            }
        });

        await().atMost(PUSH_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(context.get("status-code")).isEqualTo(RCClient.ErrorCodes.SUCCESS.ordinal());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                device.clearCache();
                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        //enable
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
        clientHandler.post(new Runnable() {
                               @Override
                               public void run() {
                                   try {
                                       device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                                   } catch (RCException e) {
                                       Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
                                   }
                               }
                           });

        await().atMost(PUSH_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(context.get("status-code")).isEqualTo(RCClient.ErrorCodes.SUCCESS.ordinal());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceUpdateParamsWithPushDisabled_Valid() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());
        final HashMap<String, Object> params = new HashMap<String, Object>();

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, PUSH_APPLICATION_NAME);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, PUSH_ACCOUNT);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, PUSH_PASSWORD);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, PUSH_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, HTTP_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, PUSH_FCM_KEY);


                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
                }
            }
        });
        await().atMost(PUSH_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(context.get("status-code")).isEqualTo(RCClient.ErrorCodes.SUCCESS.ordinal());

        deviceReconfigured = false;
        //disable
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    device.reconfigure(params);
                } catch (Exception e) {
                    Log.e(TAG, "RCDevice update Error: " + e.toString());
                }
            }
        });

        await().atMost(PUSH_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReconfigured"), equalTo(true));
        assertThat(context.get("status-code")).isEqualTo(RCClient.ErrorCodes.SUCCESS.ordinal());

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    @Test(timeout = TC_TIMEOUT)
    public void deviceUpdateParamsWithPushEnabled_Valid() throws InterruptedException {
        InstrumentationRegistry.getTargetContext().bindService(new Intent(InstrumentationRegistry.getTargetContext(), RCDevice.class), this, Context.BIND_AUTO_CREATE);
        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("serviceConnected"), equalTo(true));

        // Get the reference to the service, or you can call public methods on the binder directly.
        final RCDevice device = binder.getService();

        Handler clientHandler = new Handler(Looper.getMainLooper());
        final HashMap<String, Object> params = new HashMap<String, Object>();

        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent(RCDevice.ACTION_INCOMING_CALL, null, InstrumentationRegistry.getTargetContext(), CallActivity.class));
                params.put(RCDevice.ParameterKeys.INTENT_INCOMING_MESSAGE, new Intent(RCDevice.ACTION_INCOMING_MESSAGE, null, InstrumentationRegistry.getTargetContext(), MessageActivity.class));
                params.put(RCDevice.ParameterKeys.SIGNALING_DOMAIN, SERVER_HOST + ":" + SERVER_PORT);
                params.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, CLIENT_NAME);
                params.put(RCDevice.ParameterKeys.SIGNALING_PASSWORD, CLIENT_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, ICE_URL);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, ICE_USERNAME);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, ICE_PASSWORD);
                params.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, ICE_DOMAIN);
                params.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, true);
                params.put(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED, false);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, false);
                params.put(RCDevice.ParameterKeys.DEBUG_DISABLE_CERTIFICATE_VERIFICATION, true);

                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, PUSH_APPLICATION_NAME);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, PUSH_ACCOUNT);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, PUSH_PASSWORD);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, PUSH_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, HTTP_DOMAIN);
                params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, PUSH_FCM_KEY);


                device.setLogLevel(Log.VERBOSE);

                try {
                    device.initialize(InstrumentationRegistry.getTargetContext(), params, IntegrationTests.this);
                } catch (RCException e) {
                    Log.e(TAG, "RCDevice Initialization Error: " + e.errorText);
                }
            }
        });

        await().atMost(PUSH_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceInitialized"), equalTo(true));
        assertThat(context.get("status-code")).isEqualTo(RCClient.ErrorCodes.SUCCESS.ordinal());

        deviceReconfigured = false;

        //enable
        params.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
        clientHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    device.reconfigure(params);
                } catch (Exception e) {
                    Log.e(TAG, "RCDevice update Error: " + e.toString());
                }
            }
        });

        await().atMost(PUSH_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReconfigured"), equalTo(true));
        assertThat(context.get("status-code")).isEqualTo(RCClient.ErrorCodes.SUCCESS.ordinal());


        clientHandler.post(new Runnable() {
            @Override
            public void run() {

                device.clearCache();

                device.release();
            }
        });

        await().atMost(SIGNALING_TIMEOUT, TimeUnit.SECONDS).until(fieldIn(this).ofType(boolean.class).andWithName("deviceReleased"), equalTo(true));
        InstrumentationRegistry.getTargetContext().unbindService(this);
    }

    /**
     *
     *
     * Awaitility condition methods. Not using for now as we 're using fields directly, but let's leave around in case we need them in the future
     *
     *
     *
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
     *
     *
     * RCDeviceListener callbacks
     *
     *
     */
    public void onReconfigured(RCDevice device, RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onReconfigured");

        context.clear();
        context.put("device", device);
        context.put("connectivity-status", connectivityStatus);
        context.put("status-code", statusCode);

        deviceStartedListening = true;
        deviceReconfigured = true;
    }

    public void onError(RCDevice device, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onError: " + statusText);


        context.clear();
        context.put("device", device);
        context.put("status-code", statusCode);

        deviceStoppedListening = true;
    }

    public void onInitialized(RCDevice device, RCDeviceListener.RCConnectivityStatus connectivityStatus, int statusCode, String statusText)
    {
        Log.i(TAG, "%% onInitialized: " + statusText);

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
        context.put("job-id", jobId);

        messageAcked = true;
    }

    /**
     *
     *
     * RCConnectionListener callbacks
     *
     *
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
        Log.e(TAG, "%% onError: " + errorText);

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
     *
     *
     * Service callbacks
     *
     *
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

    /*
    @Override
    public void onStatsGathered(RCConnection connection) {

    }
    */

    /**
     *
     *
     * Broadcast receiver callbacks
     *
     *
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.e(TAG, "%% onReceive: received call");
        if (intent.getAction().equals(RCDevice.ACTION_INCOMING_CALL)) {
            connectionArrived = true;
        }
        if (intent.getAction().equals(RCDevice.ACTION_INCOMING_MESSAGE)) {
            this.context.clear();
            this.context.put("message-peer", intent.getStringExtra(RCDevice.EXTRA_DID));
            this.context.put("message-text", intent.getStringExtra(RCDevice.EXTRA_MESSAGE_TEXT));

            messageArrived = true;
        }
    }


    /**
     *
     *
     * Helper methods
     *
     *
     */
    // Check media stats and convey an error if they are not good. The reason we return a String, is that we want
    // it to be clear in the 'assert' call inside the TC what was the reason. For example if we have lost packets we will return
    // 'error: packets lost' and so the TC will fail and we'll see the assert fail with expected: 'success', got: 'error: packets lost'
    public String verifyMediaStats(String jsonStats) throws JSONException
    {
        if (jsonStats != null && jsonStats.length() > 0) {
            JSONArray mediaReportsArray = new JSONObject(jsonStats).getJSONArray("media");
            for (int i = 0; i < mediaReportsArray.length(); ++i) {
                // get current report
                JSONObject report = mediaReportsArray.getJSONObject(i);

                if (report.getString("type").equals("ssrc")) {
                    Log.i(TAG, "verifyMediaStats(): type: ssrc, id: " + report.getString("id"));
                    JSONObject values = report.getJSONObject("values");

                    // for type ssrc, sending streams contain 'send', for example 'ssrc_2321116827_send'
                    if (report.getString("id").contains("send")) {
                        // ssrc send stream
                        int bytesSent = values.getInt("bytesSent");
                        Log.i(TAG, "verifyMediaStats(): bytesSent: " + bytesSent);
                        if (values.getString("mediaType").equals("audio")) {
                            // we are using this check only for audio because in video we always get bytesSent == 0 because even though there is a local view we cannot render on it
                            if (bytesSent <= 0) {
                                return "error: audio bytes sent: " + bytesSent;
                            }

                            Log.i(TAG, "verifyMediaStats(): audioInputLevel: " + values.getInt("audioInputLevel"));
                        }
                        //int audioInputLevel = values.getInt("audioInputLevel");
                        /*
                        if (audioInputLevel <= 0) {
                            Log.e(TAG, "Error: audioInputLevel: " + audioInputLevel);
                            return false;
                        }
                        */
                    } else {
                        // For calls originating at RC/MS we cannot verify much because MS currently doesn't support RTCP XR reports,
                        // hence getStats() doesn't return anything meaningful. Hence, commenting out for now:
                        // ssrc receive stream
                        /*
                        int bytesReceived = values.getInt("bytesReceived");
                        int packetsLost = values.getInt("packetsLost");
                        Log.i(TAG, "verifyMediaStats(): bytesReceived: " + bytesReceived);
                        Log.i(TAG, "verifyMediaStats(): packetsLost: " + packetsLost);
                        if (bytesReceived <= 0) {
                            Log.e(TAG, "Error: bytesReceived: " + bytesReceived);
                            return "error: bytes received: " + bytesReceived;
                        }
                        if (packetsLost > 0) {
                            Log.e(TAG, "Error: packetsLost: " + packetsLost);
                            return "error: packets lost: " + packetsLost;
                        }

                        if (values.getString("mediaType").equals("audio")) {
                            Log.i(TAG, "verifyMediaStats(): audioOutputLevel: " + values.getInt("audioOutputLevel"));
                        }
                        */
                    }
                }
            }
        } else {
            return "error: json stats string is null or empty";
        }
        return "success";
    }

/*
    void test (){
        String smsText = "";
        // due to a bug in Restcomm (https://github.com/RestComm/Restcomm-Connect/issues/2108) To needs to have the 'client:' part removed
        params.put("To", BuildConfig.TEST_RESTCOMM_LOGIN);
        params.put("Body", smsText);
        String url = "https://" + BuildConfig.TEST_RESTCOMM_ACCOUNT_SID + ":" + BuildConfig.TEST_RESTCOMM_AUTH_TOKEN +
                "@cloud.restcomm.com/restcomm/2012-04-24/Accounts/" + BuildConfig.TEST_RESTCOMM_ACCOUNT_SID +
                "/SMS/Messages";
        doRestRequest(url, params);


    }
*/

    public void doRestRequest(String url, RequestParams params)
    {
        Log.i(TAG, "%% doRestRequest()");
        AsyncHttpClient client = new AsyncHttpClient();

        //client.setSSLSocketFactory();

        client.post(url, params, new ResponseHandlerInterface() {
            @Override
            public void sendResponseMessage(HttpResponse httpResponse) throws IOException
            {

            }

            @Override
            public void sendStartMessage() {

            }

            @Override
            public void sendFinishMessage() {

            }

            @Override
            public void sendProgressMessage(long l, long l1) {

            }

            @Override
            public void sendCancelMessage() {

            }

            @Override
            public void sendSuccessMessage(int i, Header[] headers, byte[] bytes) {
                Log.i(TAG, "%% request was handled successfully");
            }

            @Override
            public void sendFailureMessage(int i, Header[] headers, byte[] bytes, Throwable throwable) {

            }

            @Override
            public void sendRetryMessage(int i) {

            }

            @Override
            public URI getRequestURI() {
                return null;
            }

            @Override
            public void setRequestURI(URI uri) {

            }

            @Override
            public Header[] getRequestHeaders() {
                return new Header[0];
            }

            @Override
            public void setRequestHeaders(Header[] headers) {

            }

            @Override
            public boolean getUseSynchronousMode() {
                return false;
            }

            @Override
            public void setUseSynchronousMode(boolean b) {

            }

            @Override
            public boolean getUsePoolThread() {
                return false;
            }

            @Override
            public void setUsePoolThread(boolean b) {

            }

            @Override
            public void onPreProcessResponse(ResponseHandlerInterface responseHandlerInterface, HttpResponse httpResponse) {
                Log.i(TAG, "%% response is about to be processed by the system");
            }

            @Override
            public void onPostProcessResponse(ResponseHandlerInterface responseHandlerInterface, HttpResponse httpResponse) {
                Log.i(TAG, "%% request has been fully sent, handled and finished");
            }

            @Override
            public Object getTag() {
                return null;
            }

            @Override
            public void setTag(Object o) {

            }
        });
    }



/*    public SSLContext getSslContext() {

        TrustManager[] byPassTrustManagers = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }
        } };

        SSLContext sslContext=null;

        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try {
            sslContext.init(null, byPassTrustManagers, new SecureRandom());
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        return sslContext;
    }*/
}
