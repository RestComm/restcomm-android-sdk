package org.restcomm.android.sdk.util;

import android.content.Context;
import android.content.Intent;
//import android.support.test.InstrumentationRegistry;
//import android.support.test.runner.AndroidJUnit4;
import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCConnection;
import org.restcomm.android.sdk.RCDevice;
import org.robolectric.Robolectric;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restcomm.android.sdk.util.PercentFrameLayout;
import org.restcomm.android.sdk.util.RCException;
import org.restcomm.android.sdk.util.RCUtils;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 *  This class represent the tests of validation
 *  for settings parameters.
 *
 */
@RunWith(RobolectricTestRunner.class)
public class RCUtilsTest {

    private Context context;

    @Before
    public void setup() {
        context = RuntimeEnvironment.application;
    }

    @Test
    public void validateConnectionParams_Invalid() {
        HashMap<String, Object> connectParams = new HashMap<String, Object>();

        //CONNECTION_PEER missing
        try {
            RCUtils.validateConnectionParms(connectParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_CONNECTION_MISSING_PEER);
        }

        //CONNECTION_PEER empty
        connectParams = new HashMap<String, Object>();
        try {
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "");
            RCUtils.validateConnectionParms(connectParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_CONNECTION_MISSING_PEER);
        }

        connectParams = new HashMap<String, Object>();

        //CONNECTION_VIDEO_ENABLED not passed
        try {
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "restcomm");
            RCUtils.validateConnectionParms(connectParams);
        } catch (RCException ex) {
           fail(ex.toString());
        }

        //CONNECTION_LOCAL_VIDEO == null
        connectParams = new HashMap<String, Object>();
        try {
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "restcomm");
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);
            RCUtils.validateConnectionParms(connectParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_CONNECTION_VIDEO_CALL_VIEWS_MANDATORY);
        }

        //CONNECTION_REMOTE_VIDEO == null
        connectParams = new HashMap<String, Object>();
        try {
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "restcomm");
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, new PercentFrameLayout(context));
            RCUtils.validateConnectionParms(connectParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_CONNECTION_VIDEO_CALL_VIEWS_MANDATORY);

        }

    }

    @Test
    public void validateConnectionParams_Valid() {
        HashMap<String, Object> connectParams = new HashMap<String, Object>();

        //CONNECTION_PEER ok
        connectParams.put(RCConnection.ParameterKeys.CONNECTION_PEER, "restcomm");
        connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);

        connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, true);
        connectParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, new PercentFrameLayout(context));
        connectParams.put(RCConnection.ParameterKeys.CONNECTION_LOCAL_VIDEO, new PercentFrameLayout(context));
        connectParams.put(RCConnection.ParameterKeys.CONNECTION_REMOTE_VIDEO, new PercentFrameLayout(context));
        try {
            connectParams.put(RCConnection.ParameterKeys.CONNECTION_VIDEO_ENABLED, false);
            RCUtils.validateConnectionParms(connectParams);
        } catch (RCException ex) {
            fail(ex.toString());
        }
    }

    @Test
    public void validatePushParams_Invalid() {
        HashMap<String, Object> settingParams = new HashMap<String, Object>();

        //PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT not set
        try {
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_USERNAME);
        }

        //PUSH_NOTIFICATIONS_FCM_SERVER_KEY missing
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_FCM_SERVER_KEY_MISSING);
        }

        //PUSH_NOTIFICATIONS_FCM_SERVER_KEY is null
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, null);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_FCM_SERVER_KEY_MISSING);
        }

        //PUSH_NOTIFICATIONS_FCM_SERVER_KEY is ""
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_FCM_SERVER_KEY_MISSING);
        }

        //PUSH_NOTIFICATIONS_APPLICATION_NAME missing
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_NAME_MISSING);
        }

        //PUSH_NOTIFICATIONS_APPLICATION_NAME is null
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, null);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_NAME_MISSING);
        }

        //PUSH_NOTIFICATIONS_APPLICATION_NAME is ""
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_APPLICATION_NAME_MISSING);
        }

        //PUSH_NOTIFICATIONS_ACCOUNT_EMAIL missing
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_EMAIL_MISSING);
        }

        //PUSH_NOTIFICATIONS_ACCOUNT_EMAIL is null
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, null);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_EMAIL_MISSING);
        }

        //PUSH_NOTIFICATIONS_ACCOUNT_EMAIL is ""
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_EMAIL_MISSING);
        }

        //PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD missing
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_PASSWORD_MISSING);
        }

        //PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD is null
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, null);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_PASSWORD_MISSING);
        }

        //PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD is ""
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_ACCOUNT_PASSWORD_MISSING);
        }

        //PUSH_NOTIFICATIONS_PUSH_DOMAIN missing
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "pass");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_PUSH_DOMAIN_MISSING);
        }

        //PUSH_NOTIFICATIONS_PUSH_DOMAIN is null
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "pass");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, null);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_PUSH_DOMAIN_MISSING);
        }

        //PUSH_NOTIFICATIONS_PUSH_DOMAIN is ""
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "pass");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_PUSH_DOMAIN_MISSING);
        }

        //PUSH_NOTIFICATIONS_HTTP_DOMAIN missing
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "pass");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "pushdomain");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_RESTCOMM_DOMAIN_MISSING);
        }

        //PUSH_NOTIFICATIONS_HTTP_DOMAIN is null
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "pass");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "pushdomain");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, null);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_RESTCOMM_DOMAIN_MISSING);
        }

        //PUSH_NOTIFICATIONS_HTTP_DOMAIN is ""
        try {
            settingParams = new HashMap<String, Object>();
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "pass");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "pushdomain");
            settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, "");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_PUSH_NOTIFICATION_RESTCOMM_DOMAIN_MISSING);
        }
    }

    @Test
    public void validateSignalingParams_Invalid() {
        HashMap<String, Object> settingParams = new HashMap<String, Object>();

        //push settings
        //SIGNALING_USERNAME missing
        try {
            getFilledHashMapWithValidPush(settingParams);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_USERNAME);
        }

        //SIGNALING_USERNAME ""
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_USERNAME);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE wrong provided
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, 10);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {

            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_ICE_SERVER_DISCOVERY_TYPE);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE CUSTOM media ice not provided
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CUSTOM);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY_NO_ICE_SERVERS);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE CUSTOM empty media ice provided
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CUSTOM);
            List<Map<String, String>> listMediaIceServers = new ArrayList<>();
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS, listMediaIceServers);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY_NO_ICE_SERVERS);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE CUSTOM  media ice provided with no ice server url
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CUSTOM);
            List<Map<String, String>> listMediaIceServers = new ArrayList<>();
            Map<String, String> value = new HashMap<>();
            value.put("", "");
            listMediaIceServers.add(value);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS, listMediaIceServers);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE CUSTOM  MEDIA_ICE_URL provided
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledWithMediaParams(settingParams);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "media_ice_url");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE CUSTOM   MEDIA_ICE_USERNAME provided
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledWithMediaParams(settingParams);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, "media_ice_username");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE CUSTOM   MEDIA_ICE_PASSWORD provided
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledWithMediaParams(settingParams);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, "media_ice_password");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE CUSTOM   MEDIA_ICE_DOMAIN provided
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledWithMediaParams(settingParams);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_DOMAIN, "media_ice_domain");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_CUSTOM_DISCOVERY);
        }


        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE NOT CUSTOM not custom discovery
        try {
            settingParams = new HashMap<String, Object>();
            getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            List<Map<String, String>> listMediaIceServers = new ArrayList<>();
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS, listMediaIceServers);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_INVALID_ICE_SERVERS_NOT_CUSTOM_DISCOVERY);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE NOT CUSTOM missinf ice url
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3);
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_URL);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE NOT CUSTOM missing ice username
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "media_ice_url");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_USERNAME);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE NOT CUSTOM missing ice password
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "media_ice_url");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, "media_ice_username");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_PASSWORD);
        }

        //MEDIA_ICE_SERVERS_DISCOVERY_TYPE NOT CUSTOM missing ice domain
        try {
            settingParams = new HashMap<String, Object>();
            settingParams = getFilledHashMapWithValidPush(settingParams);
            settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CONFIGURATION_URL_XIRSYS_V3);
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_URL, "media_ice_url");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_USERNAME, "media_ice_username");
            settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_PASSWORD, "media_ice_password");
            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_ICE_DOMAIN);
        }
    }

    @Test
    public void validateSignalingAndPushParams_Valid() {
        HashMap<String, Object> settingParams = new HashMap<String, Object>();

        try {
            settingParams = getFilledHashMapWithValidPush(settingParams);
            getFilledWithMediaParams(settingParams);

            RCUtils.validateSettingsParms(settingParams);
        } catch (RCException ex) {
            fail(ex.toString());
        }
    }

    @Test
    public void validateDeviceParams_Invalid() {
        HashMap<String, Object> settingParams = new HashMap<String, Object>();

        try {
            settingParams = getFilledHashMapWithValidPush(settingParams);
            getFilledWithMediaParams(settingParams);

            RCUtils.validateDeviceParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_CALL_INTENT);
        }

        try {
            settingParams = getFilledHashMapWithValidPush(settingParams);
            getFilledWithMediaParams(settingParams);
            settingParams.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, "intent");

            RCUtils.validateDeviceParms(settingParams);
        } catch (RCException ex) {
            assertThat(ex.errorCode).isEqualTo(RCClient.ErrorCodes.ERROR_DEVICE_MISSING_CALL_INTENT);
        }
    }

    @Test
    public void validateDeviceParams_Valid() {
        HashMap<String, Object> settingParams = new HashMap<String, Object>();

        try {
            settingParams = getFilledHashMapWithValidPush(settingParams);
            getFilledWithMediaParams(settingParams);
            settingParams.put(RCDevice.ParameterKeys.INTENT_INCOMING_CALL, new Intent());

            RCUtils.validateDeviceParms(settingParams);
        } catch (RCException ex) {
            fail(ex.toString());
        }
    }

    //Helpers

    private HashMap<String, Object> getFilledHashMapWithValidPush(HashMap<String, Object> settingParams) {
        settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ENABLE_PUSH_FOR_ACCOUNT, true);
        settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_FCM_SERVER_KEY, "restcomm");
        settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_APPLICATION_NAME, "test app");
        settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_EMAIL, "test@test.com");
        settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_ACCOUNT_PASSWORD, "pass");
        settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_PUSH_DOMAIN, "pushdomain");
        settingParams.put(RCDevice.ParameterKeys.PUSH_NOTIFICATIONS_HTTP_DOMAIN, "httpdomain");
        return settingParams;
    }

    private HashMap<String, Object> getFilledWithMediaParams(HashMap<String, Object> settingParams) {
        getFilledHashMapWithValidPush(settingParams);
        settingParams.put(RCDevice.ParameterKeys.SIGNALING_USERNAME, "username");
        settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS_DISCOVERY_TYPE, RCDevice.MediaIceServersDiscoveryType.ICE_SERVERS_CUSTOM);
        List<Map<String, String>> listMediaIceServers = new ArrayList<>();
        Map<String, String> value = new HashMap<>();
        value.put(RCConnection.IceServersKeys.ICE_SERVER_URL, "http");
        listMediaIceServers.add(value);
        settingParams.put(RCDevice.ParameterKeys.MEDIA_ICE_SERVERS, listMediaIceServers);
        return settingParams;
    }

}
