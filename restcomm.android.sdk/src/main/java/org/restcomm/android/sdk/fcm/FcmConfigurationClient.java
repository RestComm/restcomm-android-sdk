/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.sdk.fcm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.restcomm.android.sdk.fcm.model.FcmApplication;
import org.restcomm.android.sdk.fcm.model.FcmBinding;
import org.restcomm.android.sdk.fcm.model.FcmCredentials;
import org.restcomm.android.sdk.util.RCLogger;

import android.util.Base64;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;


/**
 * Manage api calls and responses for registering push on server
 */
public class FcmConfigurationClient {

    private static final String TAG = FcmConfigurationClient.class.getCanonicalName();

    private static final String ACCOUNT_SID_URL = "/restcomm/2012-04-24/Accounts.json";
    private static final String CLIENT_SID_URL = "/restcomm/2012-04-24/Accounts";
    private static final String PUSH_PATH = "pushNotifications";
    private static final String PUSH_PATH_STAGING = "push";

    private String accountEmail;
    private String password;
    private String restcommDomain;
    private String pushDomain;
    private String pushPath;

    /**
     * Constructor
     * @param accountEmail - account's email
     * @param password - password for an account
     * @param restcommDomain - restcomm domain
     * @param pushDomain - push notification domain
    **/
    public FcmConfigurationClient(String accountEmail, String password, String pushDomain, String restcommDomain){
        this.accountEmail = accountEmail;
        this.password = password;
        this.pushDomain = pushDomain;
        this.restcommDomain = restcommDomain;

        this.pushPath = PUSH_PATH;
        if (pushDomain.equals("staging.restcomm.com")){
            this.pushPath = PUSH_PATH_STAGING;
        }

    }

    private HttpURLConnection createUrlRequestWithUrl(String urlString) throws Exception{
        return createUrlRequestWithUrl(urlString, true);
    }

    private HttpURLConnection createUrlRequestWithUrl(String urlString, boolean authorization) throws Exception{
        URL url = new URL(urlString);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestProperty("Content-Type", "application/json");
        if (authorization) {
            String authorisationString = Base64.encodeToString((accountEmail + ":" + password).getBytes(), Base64.DEFAULT);
            urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
        }
        return urlConnection;
    }

    /**
     * Returns the account sid for given email
     * @param email - account email
     * @return String - account sid
     **/
    public String getAcccountSid(String email){
        RCLogger.v(TAG, "getAcccountSid method started");
        String accountSid = null;
        try{
            String encodedEmail =  URLEncoder.encode(accountEmail, "UTF-8");
            String url = "https://" + restcommDomain + ACCOUNT_SID_URL + "/" + encodedEmail;
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                String jsonString = getJsonFromStream(connection.getInputStream());
                if (jsonString.length() > 0) {
                    JSONObject inputJson = new JSONObject(jsonString);
                    accountSid = inputJson.getString("sid");
                }
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "getAcccountSid method ended; returning account sid: " + accountSid);
        return accountSid;
    }


    /**
     * Returns the client sid for given account sid and username
     * @param accountSid - account sid
     * @param username - signaling username
     * @return String - client sid
     **/
    public String getClientSid(String accountSid, String username){
        RCLogger.v(TAG, "getClientSid method started");
        String clientSid = null;
        try{
            String url = "https://" + restcommDomain + CLIENT_SID_URL + "/" + accountSid + "/Clients.json";
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                String jsonString = getJsonFromStream(connection.getInputStream());
                if (jsonString.length() > 0) {
                    JSONArray inputJson = new JSONArray(jsonString);
                    for (int i = 0; i < inputJson.length(); i++){
                        JSONObject client = inputJson.getJSONObject(i);
                        String clientLogin = client.getString("login");
                        if (clientLogin.equals(username)){
                            clientSid = client.getString("sid");
                            break;
                        }
                    }
                }
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "getClientSid method ended; returning client sid: " + clientSid);
        return clientSid;
    }


    /**
     * Enables or disables push notification option on server for a specific client
     * @param enable - if true, push settings will be enabled on server for the account, otherwise it will be disabled
     * @param accountSid - account sid
     * @param clientSid - client sid
     * @return boolean - true if no errors, otherwise false
     */
    public boolean enableClientPushSettings(boolean enable, String accountSid, String clientSid){
        boolean ok = false;
        RCLogger.v(TAG, "enableClientPushSettings method started");
        FcmCredentials fcmCredentials = null;
        try{
            String url = "https://" + accountSid + ":" + this.password + "@" + this.restcommDomain + CLIENT_SID_URL + "/" + accountSid + "/Clients/" + clientSid;
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url, false);
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", "txt/html");
            String outputString =
                    "{\n" +
                            "  \"IsPushEnabled\": \"" + enable + "\"\n" +
                            "}";
            OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream());
            outputStream.write(outputString.getBytes());
            outputStream.close();
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                ok = true;
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "enableClientPushSettings method ended; returning ok:" + ok);
        return ok;

    }

    /**
     * Returns the FcmApplication object for given application name
     * @param applicationName - application name
     * @return FcmApplication - object if everything is okay, otherwise null
     **/
    public FcmApplication getApplication(String applicationName){
        RCLogger.v(TAG, "getApplication method started");
        FcmApplication fcmApplication = null;
        try{
            String url = "https://" + pushDomain + "/" + this.pushPath + "/applications";
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                fcmApplication = getApplicationFromConnection(connection, applicationName);
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "getApplication method ended; returning fcmApplication");
        return fcmApplication;
    }


    /**
    * Creates an application on server for the given FcmApplication object
    * @param application - RCApplication object
    * @return FcmApplication - object if everything is okay, otherwise null
    **/
    public FcmApplication createApplication(FcmApplication application){
        RCLogger.v(TAG, "createApplication method started");
        FcmApplication fcmApplication = null;
        try{
            String url = "https://" + pushDomain + "/" + this.pushPath + "/applications";
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            String outputString =
                    "{\n" +
                        "  \"FriendlyName\": \"" + application.getFriendlyName() + "\"\n" +
                    "}";
            OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream());
            outputStream.write(outputString.getBytes());
            outputStream.close();
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                fcmApplication = getApplicationFromConnection(connection, application.getFriendlyName());
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "createApplication method ended; returning fcmApplication");
        return fcmApplication;
    }

    /**
     * Returns the FcmCredentials object for given application
     * @param application - FcmApplication object
     * @return FcmCredentials - object if everything is okay, otherwise null
    **/
    public FcmCredentials getCredentials(FcmApplication application){
        RCLogger.v(TAG, "getCredentials method started");
        FcmCredentials fcmCredentials = null;
        try{
            String url = "https://" + pushDomain + "/" + this.pushPath + "/credentials";
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                fcmCredentials = getCredentialsFromConnection(connection, application.getSid());
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "getCredentials method ended; returning fcmCredentials");
        return fcmCredentials;
    }

    /**
    * Creates credentials on server for the given FcmCredentials object
    * @param credentials - FcmCredentials object
    * @param fcmSecret - Server key from firebase client
    * @return FcmCredentials - object if everything is okay, otherwise null
    **/
    public FcmCredentials createCredentials(FcmCredentials credentials, String fcmSecret){
        RCLogger.v(TAG, "createCredentials method started");
        FcmCredentials fcmCredentials = null;
        try{
            String url = "https://" + pushDomain + "/" + this.pushPath + "/credentials";
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            String outputString =
                    "{\n" +
                            "  \"ApplicationSid\": \"" + credentials.getApplicationSid() + "\",\n" +
                            "  \"CredentialType\": \""+ credentials.getCredentialType() +"\",\n" +
                            "  \"Secret\": \"" + fcmSecret + "\"\n" +
                            "}";
            OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream());
            outputStream.write(outputString.getBytes());
            outputStream.close();
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                fcmCredentials = getCredentialsFromConnection(connection, credentials.getApplicationSid());
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "createCredentials method ended; returning fcmCredentials");
        return fcmCredentials;
    }

    /**
    * Returns the FcmBinding object for given application
    * @param  application - FcmApplication object
    * @return FcmBinding - object if everything is okay, otherwise null
    */
    public FcmBinding getBinding(FcmApplication application){
        RCLogger.v(TAG, "getBinding method started");
        FcmBinding fcmBinding = null;
        try{
            String url = "https://" + pushDomain + "/" + this.pushPath + "/bindings";
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                fcmBinding = getBindingFromConnection(connection, application.getSid());
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "getBinding method ended; returning fcmBinding");
        return fcmBinding;
    }


    /**
     * Creates binding on server for the given FcmBinding object
     * @param binding - FcmBinding object
     * @return FcmBinding - object if everything is okay, otherwise null
     */
    public FcmBinding createBinding(FcmBinding binding){
        return this.createOrUpdateBinding(binding, null);
    }

    /**
     * Update binding on server for given FcmBinding object
     * @param binding
     * @return
     */
    public FcmBinding updateBinding(FcmBinding binding){
        return this.createOrUpdateBinding(binding, binding.getSid());
    }

    /**
     * Creates or update binding on server for the given FcmBinding object
     * @param binding - FcmBinding object
     * @param bindingSid - binding sid, if null its creating new binding, otherwise its update
     * @return FcmBinding - object if everything is okay, otherwise null
     */
    private FcmBinding createOrUpdateBinding(FcmBinding binding, String bindingSid){
        RCLogger.v(TAG, "createBinding method started");
        FcmBinding fcmBinding = null;
        try{
            String url = "https://" + pushDomain + "/" + this.pushPath + "/bindings";
            if (bindingSid != null){
                url = "https://" + pushDomain + "/" + this.pushPath + "/bindings/" + bindingSid;
            }
            RCLogger.v(TAG, "calling url: " + url);
            HttpURLConnection connection = createUrlRequestWithUrl(url);
            connection.setRequestMethod("POST");
            if (bindingSid != null){
                connection.setRequestMethod("PUT");
            }
            connection.setRequestProperty("Content-Type", "application/json");
            String outputString =
                    "{\n" +
                            "  \"Identity\": \"" + binding.getIdentity() + "\",\n" +
                            "  \"ApplicationSid\": \"" + binding.getApplicationSid() + "\",\n" +
                            "  \"BindingType\": \"" + binding.getBindingType() + "\",\n" +
                            "  \"Address\": \"" + binding.getAddress() + "\"\n" +
                            "}";
            OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream());
            outputStream.write(outputString.getBytes());
            outputStream.close();
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                fcmBinding = getBindingFromConnection(connection, binding.getApplicationSid());
            }

        }catch (Exception ex){
            RCLogger.e(TAG, ex.toString());
        }
        RCLogger.v(TAG, "createBinding method ended; returning fcmBinding");
        return fcmBinding;
    }




    //-------------------------Helper methods -----------------------------//

    private FcmApplication getApplicationFromConnection(HttpURLConnection connection, String applicationName) throws IOException, JSONException{
        String jsonString = getJsonFromStream(connection.getInputStream());
        if (jsonString.length() > 0) {
            Object json = new JSONTokener(jsonString).nextValue();
            if (json instanceof  JSONArray){
                JSONArray inputJson = new JSONArray(jsonString);
                for (int i = 0; i < inputJson.length(); i++){
                    JSONObject client = inputJson.getJSONObject(i);
                    return extractFromJsonObjectApplication(client, applicationName);
                }
            } else {
                JSONObject client = new JSONObject(jsonString);
                return extractFromJsonObjectApplication(client, applicationName);
            }

        }
        return null;
    }

    private FcmApplication extractFromJsonObjectApplication(JSONObject client, String applicationName) throws IOException, JSONException{
        String friendlyName = client.getString("FriendlyName");
        if (friendlyName.equals(applicationName)){
            String sid = client.getString("Sid");
            String friendlyNameStr = client.getString("FriendlyName");
            return new FcmApplication(sid, friendlyNameStr);
        }
        return null;
    }

    private FcmCredentials getCredentialsFromConnection(HttpURLConnection connection, String applicationSid) throws IOException, JSONException{
        String jsonString = getJsonFromStream(connection.getInputStream());
        if (jsonString.length() > 0) {
            Object json = new JSONTokener(jsonString).nextValue();
            if (json instanceof  JSONArray){
                JSONArray inputJson = new JSONArray(jsonString);
                for (int i = 0; i < inputJson.length(); i++){
                    JSONObject client = inputJson.getJSONObject(i);
                    FcmCredentials credentials = extractFromJsonObjectCredentials(client, applicationSid);
                    if (credentials != null){
                        return credentials;
                    }
                }
            } else {
                JSONObject client = new JSONObject(jsonString);
                return extractFromJsonObjectCredentials(client, applicationSid);
            }
        }
        return null;
    }

    private FcmCredentials extractFromJsonObjectCredentials(JSONObject client, String applicationSid) throws IOException, JSONException{
        String applicationSidServer = client.getString("ApplicationSid");
        String inputCredentialType = client.getString("CredentialType");
        if (applicationSidServer.equals(applicationSid) && inputCredentialType.equals("fcm")){
            String sid = client.getString("Sid");
            return new FcmCredentials(sid, applicationSidServer, inputCredentialType);
        }
        return null;
    }

    private FcmBinding getBindingFromConnection(HttpURLConnection connection, String applicationSid) throws IOException, JSONException{
        String jsonString = getJsonFromStream(connection.getInputStream());
        if (jsonString.length() > 0) {
            Object json = new JSONTokener(jsonString).nextValue();
            if (json instanceof  JSONArray){
                JSONArray inputJson = new JSONArray(jsonString);
                for (int i = 0; i < inputJson.length(); i++){
                    JSONObject client = inputJson.getJSONObject(i);
                    FcmBinding binding = extractFromJsonObjectBinding(client, applicationSid);
                    if (binding!=null){
                        return binding;
                    }
                }
            } else {
                JSONObject client = new JSONObject(jsonString);
                return extractFromJsonObjectBinding(client, applicationSid);
            }

        }
        return null;
    }

    private FcmBinding extractFromJsonObjectBinding(JSONObject client, String applicationSid)  throws IOException, JSONException {
        String applicationSidServer = client.getString("ApplicationSid");
        String bindingType = client.getString("BindingType");
        if (applicationSidServer.equals(applicationSid) && bindingType.equals("fcm")){
            String sid = client.getString("Sid");
            String identity = client.getString("Identity");
            String address = client.getString("Address");

            return new FcmBinding(sid, identity, applicationSid, bindingType, address);
        }
        return null;
    }


    private String getJsonFromStream(InputStream is) throws IOException{

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
            sb.append("\n");
        }
        is.close();
        return sb.toString();
    }

}
