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

import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.restcomm.android.sdk.RCDevice;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FcmMessages {

   private static final String TAG = "FcmMessages";

   /**
    * Parameter keys for FcmMessages
    */
   public static class ParameterKeys {
      public static final String RESTCOMM_ACCOUNT_EMAIL = "restcomm-account-email";
      public static final String RESTCOMM_ACCOUNT_PASSWORD = "restcomm-account-password";
      public static final String RESTCOMM_ACCOUNT_SID = "restcomm-account-sid";
      public static final String RESTCOMM_CLIENT_SID = "restcomm-client-sid";
      public static final String PUSH_NOTIFICATION_ENABLED = "push-notification-enabled";
      public static final String PUSH_NOTIFICATION_DOMAIN = "push-notification-domain";
      public static final String PUSH_NOTIFICATION_APPLICATION_SID = "push-notification-application-sid";
      public static final String PUSH_NOTIFICATION_CREDENTIALS_SID = "push-notification-credentials-sid";
      public static final String PUSH_NOTIFICATION_BINDING_SID = "push-notification-binding-sid";
      public static final String PUSH_NOTIFICATION_FCM_SECRET = "push-notification-fcm-secret";
   }

   private static FcmMessages instance = null;
   private Context context = null;
   private FcmMessageListener listener = null;

   SharedPreferences prefs;

   protected FcmMessages() {
   }

   public static FcmMessages getInstance() {
      if (instance == null) {
         instance = new FcmMessages();
      }
      return instance;
   }

   public void sendNotification(String from, String message) {
      if (listener != null) {
         Log.i(TAG, "FCM Message received - from: " + from + ", message: " + message);
         listener.onFcmMessageReceived(from, message);
      }
   }

   public void setApplicationContext(Context context) {
      this.context = context;
      prefs = PreferenceManager.getDefaultSharedPreferences(context);
   }

   public void setMessageListener(FcmMessageListener listener) {
      this.listener = listener;
   }

   public void removeMessageListener() {
     this.listener = null;
   }

   public class SetRpnBindingTask extends AsyncTask<Void, Void, Void> {

      protected Void doInBackground(Void... params) {

         if (context == null)
            return null;

         try {
            if (getPushNotificationEnabled()) {
               String clientSid = getRestcommClientSid();
               if (clientSid.length() == 0)
                  obtainClientSid();

               if (!checkExistingBinding())
                  createOrUpdateBinding();
            } else {
               deletePushNotificationData();
            }
         } catch (RuntimeException exc) {
            Log.e(TAG, "FCM Error: " + exc.getMessage());
         }
         return null;
      }

      protected void onProgressUpdate() {
      }

      protected void onPostExecute() {
      }

      private void obtainClientSid() {

         HttpURLConnection urlConnection = null;
         try {
            String urlString;
            URL url;
            String accountSid = getRestcommAccountSid();
            if (accountSid.length() == 0) {
               urlString = "https://" + getSignallingDomain() +
                     "/restcomm/2012-04-24/Accounts.json/" + getRestcommAccountEmail().replace("@", "%40");
               url = new URL(urlString);
               urlConnection = (HttpURLConnection) url.openConnection();
               urlConnection.setRequestMethod("GET");
               urlConnection.setRequestProperty("Content-Type", "application/json");
               String accountEmail = getRestcommAccountEmail();
               String accountPassword = getRestcommAccountPassword();
               String authorisationString = Base64.encodeToString((accountEmail + ":" + accountPassword).getBytes(), Base64.DEFAULT);
               urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
               int responseCode = urlConnection.getResponseCode();
               if (responseCode == HttpURLConnection.HTTP_OK) {
                  StringBuilder inputStringBuilder = new StringBuilder();
                  InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                  BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
                  String inputLine;
                  while ((inputLine = inputStreamReader.readLine()) != null)
                     inputStringBuilder.append(inputLine);
                  inputStream.close();
                  String input = inputStringBuilder.toString();
                  if (input.length() == 0) {
                     Log.e(TAG, "Wrong account email: " + getRestcommAccountEmail());
                     return;
                  }
                  JSONObject inputJson = new JSONObject(input);
                  accountSid = inputJson.getString("sid");
                  setRestcommAccountSid(accountSid);
                  Log.i(TAG, "RC Account SID set: " + accountSid);
               } else {
                  Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
               }
            }
            String clientSid = getRestcommClientSid();
            if (accountSid.length() > 0 && clientSid.length() == 0) {
               urlString = "https://" + getSignallingDomain() +
                     "/restcomm/2012-04-24/Accounts/" + accountSid + "/Clients.json";
               url = new URL(urlString);
               urlConnection = (HttpURLConnection) url.openConnection();
               urlConnection.setRequestMethod("GET");
               urlConnection.setRequestProperty("Content-Type", "application/json");
               String accountEmail = getRestcommAccountEmail();
               String accountPassword = getRestcommAccountPassword();
               String authorisationString = Base64.encodeToString((accountEmail + ":" + accountPassword).getBytes(), Base64.DEFAULT);
               urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
               int responseCode = urlConnection.getResponseCode();
               if (responseCode == HttpURLConnection.HTTP_OK) {
                  StringBuilder inputStringBuilder = new StringBuilder();
                  InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                  BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
                  String inputLine;
                  while ((inputLine = inputStreamReader.readLine()) != null)
                     inputStringBuilder.append(inputLine);
                  inputStream.close();
                  JSONArray inputJson = new JSONArray(inputStringBuilder.toString());
                  for (int i = 0; i < inputJson.length(); i++) {
                     JSONObject client = inputJson.getJSONObject(i);
                     String clientLogin = client.getString("login");
                     String signallingUsername = getSignallingUsername();
                     if (signallingUsername.length() == 0) {
                        Log.w(TAG, "SIP username hasn't been set yet, cannot setup push notifications now");
                        return;
                     }
                     if (clientLogin.equals(signallingUsername)) {
                        clientSid = client.getString("sid");
                        setRestcommClientSid(clientSid);
                        Log.i(TAG, "RC Client SID set: " + clientSid);
                        break;
                     }
                  }
               } else {
                  Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
               }
            }
         } catch (MalformedURLException e) {
            throw new RuntimeException("Wrong RPN server URL", e);
         } catch (IOException e) {
            throw new RuntimeException("RPN server communication error", e);
         } catch (JSONException e) {
            throw new RuntimeException("Wrong JSON format in RPN response message", e);
         } finally {
            if (urlConnection != null)
               urlConnection.disconnect();
         }
      }

      private boolean checkExistingBinding() {

         HttpURLConnection urlConnection = null;
         try {
            String bindingSid = getPushNotificationBindingSid();
            if (bindingSid.length() == 0)
               return false;
            String urlString = "https://" + getPushNotificationDomain() +
                  "/pushNotifications/bindings/" + bindingSid;
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            String accountEmail = getRestcommAccountEmail();
            String accountPassword = getRestcommAccountPassword();
            String authorisationString = Base64.encodeToString((accountEmail + ":" + accountPassword).getBytes(), Base64.DEFAULT);
            urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
               InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
               BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
               boolean bindingExists;
               if (inputStreamReader.readLine() != null) {
                  bindingExists = true;
               } else {
                  bindingExists = false;
                  setPushNotificationBindingSid(null);
                  Log.w(TAG, "Binding has not been found on the server. Removing local record.");
               }
               inputStream.close();
               return bindingExists;
            } else {
               Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
            }
         } catch (MalformedURLException e) {
            throw new RuntimeException("Wrong RPN server URL", e);
         } catch (IOException e) {
            throw new RuntimeException("RPN server communication error", e);
         } finally {
            if (urlConnection != null)
               urlConnection.disconnect();
         }
         return false;
      }

      private void createOrUpdateBinding() {

         HttpURLConnection urlConnection = null;
         try {
            String urlString;
            URL url;
            String applicationSid = getPushNotificationApplicationSid();
            if (applicationSid.length() == 0) {
               urlString = "https://" + getPushNotificationDomain() + "/pushNotifications/applications";
               url = new URL(urlString);
               urlConnection = (HttpURLConnection) url.openConnection();
               urlConnection.setRequestMethod("GET");
               urlConnection.setRequestProperty("Content-Type", "application/json");
               String accountEmail = getRestcommAccountEmail();
               String accountPassword = getRestcommAccountPassword();
               String authorisationString = Base64.encodeToString((accountEmail + ":" + accountPassword).getBytes(), Base64.DEFAULT);
               urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
               int responseCode = urlConnection.getResponseCode();
               if (responseCode == HttpURLConnection.HTTP_OK) {
                  StringBuilder inputStringBuilder = new StringBuilder();
                  InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                  BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
                  String inputLine;
                  while ((inputLine = inputStreamReader.readLine()) != null)
                     inputStringBuilder.append(inputLine);
                  inputStream.close();
                  JSONArray inputJsonArray = new JSONArray(inputStringBuilder.toString());
                  boolean applicationFound = false;
                  for (int i = 0; i < inputJsonArray.length(); i++) {
                     JSONObject application = inputJsonArray.getJSONObject(i);
                     String applicationName = application.getString("FriendlyName");
                     if (applicationName.equals("Olympus")) {
                        applicationSid = application.getString("Sid");
                        setPushNotificationApplicationSid(applicationSid);
                        Log.i(TAG, "Push Notification Application SID set: " + applicationSid);
                        applicationFound = true;
                        break;
                     }
                  }
                  if (!applicationFound) {
                     urlString = "https://" + getPushNotificationDomain() + "/pushNotifications/applications";
                     url = new URL(urlString);
                     urlConnection = (HttpURLConnection) url.openConnection();
                     urlConnection.setRequestMethod("POST");
                     urlConnection.setRequestProperty("Content-Type", "application/json");
                     urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
                     String outputString =
                           "{\n" +
                           "  \"FriendlyName\": \"Olympus\"\n" +
                           "}";
                     OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
                     outputStream.write(outputString.getBytes());
                     outputStream.close();
                     responseCode = urlConnection.getResponseCode();
                     if (responseCode == HttpURLConnection.HTTP_OK) {
                        inputStringBuilder = new StringBuilder();
                        inputStream = new BufferedInputStream(urlConnection.getInputStream());
                        inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
                        while ((inputLine = inputStreamReader.readLine()) != null)
                           inputStringBuilder.append(inputLine);
                        inputStream.close();
                        JSONObject inputJsonObject = new JSONObject(inputStringBuilder.toString());
                        applicationSid = inputJsonObject.getString("Sid");
                        setPushNotificationApplicationSid(applicationSid);
                        Log.i(TAG, "Push Notification Application SID set: " + applicationSid);
                     } else {
                        Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
                     }

                  }
               } else {
                  Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
               }
            }
            String credentialsSid = getPushNotificationCredentialsSid();
            if (applicationSid.length() != 0 && credentialsSid.length() == 0) {
               urlString = "https://" + getPushNotificationDomain() + "/pushNotifications/credentials";
               url = new URL(urlString);
               urlConnection = (HttpURLConnection) url.openConnection();
               urlConnection.setRequestMethod("GET");
               urlConnection.setRequestProperty("Content-Type", "application/json");
               String accountEmail = getRestcommAccountEmail();
               String accountPassword = getRestcommAccountPassword();
               String authorisationString = Base64.encodeToString((accountEmail + ":" + accountPassword).getBytes(), Base64.DEFAULT);
               urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
               int responseCode = urlConnection.getResponseCode();
               if (responseCode == HttpURLConnection.HTTP_OK) {
                  StringBuilder inputStringBuilder = new StringBuilder();
                  InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                  BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
                  String inputLine;
                  while ((inputLine = inputStreamReader.readLine()) != null)
                     inputStringBuilder.append(inputLine);
                  inputStream.close();
                  JSONArray inputJsonArray = new JSONArray(inputStringBuilder.toString());
                  boolean credentialsFound = false;
                  for (int i = 0; i < inputJsonArray.length(); i++) {
                     JSONObject credentials = inputJsonArray.getJSONObject(i);
                     String inputApplicationSid = credentials.getString("ApplicationSid");
                     String inputCredentialType = credentials.getString("CredentialType");
                     if (inputApplicationSid.equals(applicationSid) && inputCredentialType.equals("fcm")) {
                        credentialsSid = credentials.getString("Sid");
                        setPushNotificationCredentialsSid(credentialsSid);
                        Log.i(TAG, "Push Notification Credentials SID set: " + credentialsSid);
                        credentialsFound = true;
                        break;
                     }
                  }

                  String fcmSecret = getPushNotificationFcmSecret();
                  if (!credentialsFound) {
                     if (fcmSecret.length() != 0) {
                        urlString = "https://" + getPushNotificationDomain() + "/pushNotifications/credentials";
                        url = new URL(urlString);
                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setRequestMethod("POST");
                        urlConnection.setRequestProperty("Content-Type", "application/json");
                        urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
                        String outputString =
                              "{\n" +
                              "  \"ApplicationSid\": \"" + applicationSid + "\",\n" +
                              "  \"CredentialType\": \"fcm\",\n" +
                              "  \"Secret\": \"" + fcmSecret + "\"\n" +
                              "}";
                        OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
                        outputStream.write(outputString.getBytes());
                        outputStream.close();
                        responseCode = urlConnection.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                           inputStringBuilder = new StringBuilder();
                           inputStream = new BufferedInputStream(urlConnection.getInputStream());
                           inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
                           while ((inputLine = inputStreamReader.readLine()) != null)
                              inputStringBuilder.append(inputLine);
                           inputStream.close();
                           JSONObject inputJsonObject = new JSONObject(inputStringBuilder.toString());
                           credentialsSid = inputJsonObject.getString("Sid");
                           setPushNotificationCredentialsSid(credentialsSid);
                           Log.i(TAG, "Push Notification Credentials SID set: " + credentialsSid);
                        } else {
                           Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
                        }
                     } else {
                        Log.w(TAG, "FCM Server Token has not been set");
                     }
                  }
               } else {
                  Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
               }
            }
            String clientSid = getRestcommClientSid();
            if (applicationSid.length() == 0 || credentialsSid.length() == 0 || clientSid.length() == 0)
               return;
            String bindingSid = getPushNotificationBindingSid();
            urlString = "https://" + getPushNotificationDomain() + "/pushNotifications/bindings";
            if (bindingSid.length() > 0)
               urlString += "/" + bindingSid;
            url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            if (bindingSid.length() > 0)
               urlConnection.setRequestMethod("PUT");
            else
               urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            String accountEmail = getRestcommAccountEmail();
            String accountPassword = getRestcommAccountPassword();
            String authorisationString = Base64.encodeToString((accountEmail + ":" + accountPassword).getBytes(), Base64.DEFAULT);
            urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
            String fcmRegistrationToken = FirebaseInstanceId.getInstance().getToken();
            String outputString =
                  "{\n" +
                  "  \"Identity\": \"" + clientSid + "\",\n" +
                  "  \"ApplicationSid\": \"" + applicationSid + "\",\n" +
                  "  \"BindingType\": \"fcm\",\n" +
                  "  \"Address\": \"" + fcmRegistrationToken + "\"\n" +
                  "}";
            OutputStream outputStream = new BufferedOutputStream(urlConnection.getOutputStream());
            outputStream.write(outputString.getBytes());
            outputStream.close();
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
               StringBuilder inputStringBuilder = new StringBuilder();
               InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
               BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
               String inputLine;
               while ((inputLine = inputStreamReader.readLine()) != null)
                  inputStringBuilder.append(inputLine);
               inputStream.close();
               JSONObject inputJson = new JSONObject(inputStringBuilder.toString());
               if (bindingSid.length() == 0) {
                  bindingSid = inputJson.getString("Sid");
                  setPushNotificationBindingSid(bindingSid);
                  Log.i(TAG, "Push Notification Binding SID set: " + bindingSid);
               }
            } else {
               Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
            }
         } catch (MalformedURLException e) {
            throw new RuntimeException("Wrong RPN server URL", e);
         } catch (IOException e) {
            throw new RuntimeException("RPN server communication error", e);
         } catch (JSONException e) {
            throw new RuntimeException("Wrong JSON format in RPN response message", e);
         } finally {
            if (urlConnection != null)
               urlConnection.disconnect();
         }
      }

      private void deletePushNotificationData() {

         HttpURLConnection urlConnection = null;
         try {
            List<String> deleteBindingSidArray = new ArrayList<String>();
            String clientSid = getRestcommClientSid();
            String pushApplicationSid = getPushNotificationApplicationSid();
            if (clientSid.length() == 0 || pushApplicationSid.length() == 0)
               return;
            String urlString = "https://" + getPushNotificationDomain() +
                  "/pushNotifications/bindings";
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("Content-Type", "application/json");
            String accountEmail = getRestcommAccountEmail();
            String accountPassword = getRestcommAccountPassword();
            String authorisationString = Base64.encodeToString((accountEmail + ":" + accountPassword).getBytes(), Base64.DEFAULT);
            urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
            int responseCode = urlConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
               StringBuilder inputStringBuilder = new StringBuilder();
               InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
               BufferedReader inputStreamReader = new BufferedReader(new InputStreamReader(inputStream));
               String inputLine;
               while ((inputLine = inputStreamReader.readLine()) != null)
                  inputStringBuilder.append(inputLine);
               inputStream.close();
               JSONArray inputJson = new JSONArray(inputStringBuilder.toString());
               for (int i = 0; i < inputJson.length(); i++) {
                  JSONObject binding = inputJson.getJSONObject(i);
                  String inputClientSid = binding.getString("Identity");
                  String inputApplicationSid = binding.getString("ApplicationSid");
                  String inputBindingType = binding.getString("BindingType");
                  if (inputClientSid.equals(clientSid) && inputApplicationSid.equals(pushApplicationSid) &&
                        inputBindingType.equals("fcm")) {
                     String bindingSid = binding.getString("Sid");
                     deleteBindingSidArray.add(bindingSid);
                  }
               }
            } else {
               Log.e(TAG, "RC Service communication error - URL: " + urlString + ", Response code: " + responseCode);
            }
            for (String bindingSid : deleteBindingSidArray) {
               urlString = "https://" + getPushNotificationDomain() +
                     "/pushNotifications/bindings/" + bindingSid;
               url = new URL(urlString);
               urlConnection = (HttpURLConnection) url.openConnection();
               urlConnection.setRequestMethod("DELETE");
               urlConnection.setRequestProperty("Content-Type", "application/json");
               urlConnection.setRequestProperty("Authorization", "Basic " + authorisationString);
               responseCode = urlConnection.getResponseCode();
               if (responseCode == HttpURLConnection.HTTP_OK) {
                  Log.i(TAG, "Push Notification Binding deleted: " + bindingSid);
               }
            }
            setRestcommAccountSid(null);
            setRestcommClientSid(null);
            setPushNotificationApplicationSid(null);
            setPushNotificationCredentialsSid(null);
            setPushNotificationBindingSid(null);
            Log.i(TAG, "All Push Notification Binding internal data removed.");
         } catch (MalformedURLException e) {
            throw new RuntimeException("Wrong RPN server URL", e);
         } catch (IOException e) {
            throw new RuntimeException("RPN server communication error", e);
         } catch (JSONException e) {
            throw new RuntimeException("Wrong JSON format in RPN response message", e);
         } finally {
            if (urlConnection != null)
               urlConnection.disconnect();
         }
      }

      private String getRestcommAccountEmail() {
         return prefs.getString(ParameterKeys.RESTCOMM_ACCOUNT_EMAIL, "administrator@company.com");
      }

      private void setRestcommAccountEmail(String value) {
         setPreferenceValue(ParameterKeys.RESTCOMM_ACCOUNT_EMAIL, value);
      }

      private String getRestcommAccountPassword() {
         return prefs.getString(ParameterKeys.RESTCOMM_ACCOUNT_PASSWORD, "");
      }

      private void setRestcommAccountPassword(String value) {
         setPreferenceValue(ParameterKeys.RESTCOMM_ACCOUNT_PASSWORD, value);
      }

      private String getRestcommAccountSid() {
         return prefs.getString(ParameterKeys.RESTCOMM_ACCOUNT_SID, "");
      }

      private void setRestcommAccountSid(String value) {
         setPreferenceValue(ParameterKeys.RESTCOMM_ACCOUNT_SID, value);
      }

      private String getRestcommClientSid() {
         return prefs.getString(ParameterKeys.RESTCOMM_CLIENT_SID, "");
      }

      private void setRestcommClientSid(String value) {
         setPreferenceValue(ParameterKeys.RESTCOMM_CLIENT_SID, value);
      }

      private boolean getPushNotificationEnabled() {
         return prefs.getBoolean(ParameterKeys.PUSH_NOTIFICATION_ENABLED, true);
      }

      private void setPushNotificationEnabled(String value) {
         setPreferenceValue(ParameterKeys.PUSH_NOTIFICATION_ENABLED, value);
      }

      private String getPushNotificationDomain() {
         return prefs.getString(ParameterKeys.PUSH_NOTIFICATION_DOMAIN, "push.restcomm.com");
      }

      private void setPushNotificationDomain(String value) {
         setPreferenceValue(ParameterKeys.PUSH_NOTIFICATION_DOMAIN, value);
      }

      private String getPushNotificationApplicationSid() {
         return prefs.getString(ParameterKeys.PUSH_NOTIFICATION_APPLICATION_SID, "");
      }

      private void setPushNotificationApplicationSid(String value) {
         setPreferenceValue(ParameterKeys.PUSH_NOTIFICATION_APPLICATION_SID, value);
      }

      private String getPushNotificationCredentialsSid() {
         return prefs.getString(ParameterKeys.PUSH_NOTIFICATION_CREDENTIALS_SID, "");
      }

      private void setPushNotificationCredentialsSid(String value) {
         setPreferenceValue(ParameterKeys.PUSH_NOTIFICATION_CREDENTIALS_SID, value);
      }

      private String getPushNotificationBindingSid() {
         return prefs.getString(ParameterKeys.PUSH_NOTIFICATION_BINDING_SID, "");
      }

      private void setPushNotificationBindingSid(String value) {
         setPreferenceValue(ParameterKeys.PUSH_NOTIFICATION_BINDING_SID, value);
      }

      private String getPushNotificationFcmSecret() {
         //todo: oggie
         return prefs.getString(ParameterKeys.PUSH_NOTIFICATION_FCM_SECRET, "");
      }

      private void setPushNotificationFcmSecret(String value) {
         setPreferenceValue(ParameterKeys.PUSH_NOTIFICATION_FCM_SECRET, value);
      }

      private String getSignallingDomain() {
         return prefs.getString(RCDevice.ParameterKeys.SIGNALING_DOMAIN, "cloud.restcomm.com");
      }

      private String getSignallingUsername() {
         return prefs.getString(RCDevice.ParameterKeys.SIGNALING_USERNAME, "");
      }

      private void setPreferenceValue(String key, boolean value) {
         SharedPreferences.Editor prefsEdit = prefs.edit();
         prefsEdit.putBoolean(key, value);
         prefsEdit.apply();
      }

      private void setPreferenceValue(String key, String value) {
         SharedPreferences.Editor prefsEdit = prefs.edit();
         if (value != null) {
            prefsEdit.putString(key, value);
         } else {
            prefsEdit.remove(key);
         }
         prefsEdit.apply();
      }
   }
}
