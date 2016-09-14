/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
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

/*
 * libjingle
 * Copyright 2014 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.restcomm.android.sdk.MediaClient.util;

//import org.appspot.apprtc.AppRTCClient.SignalingParameters;
//import org.appspot.apprtc.util.AsyncHttpURLConnection;
//import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.util.LinkedList;

// Fetches the ICE Servers asynchronously and provides callbacks for result
public class IceServerFetcher {
    private static final String TAG = "IceServerFetcher";
    private final IceServerFetcherEvents events;
    private final String iceUrl;
    private boolean turnEnabled = true;
    private AsyncHttpURLConnection httpConnection;

    /**
     * Room parameters fetcher callbacks.
     */
    public static interface IceServerFetcherEvents {
        /**
         * Callback fired when ICE servers are fetched
         */
        public void onIceServersReady(final LinkedList<PeerConnection.IceServer> iceServers);

        /**
         * Callback if there's an error fetching ICE servers
         */
        public void onIceServersError(final String description);
    }

    public IceServerFetcher(String iceUrl, boolean turnEnabled, final IceServerFetcherEvents events) {
        this.iceUrl = iceUrl;
        this.turnEnabled = turnEnabled;
        this.events = events;
    }

    public void makeRequest() {
        Log.d(TAG, "Requesting ICE servers from: " + iceUrl);
        httpConnection = new AsyncHttpURLConnection(
                "GET", iceUrl,
                new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.e(TAG, "ICE servers request timeout: " + errorMessage);
                        events.onIceServersError(errorMessage);
                    }

                    @Override
                    public void onHttpComplete(String response) {
                        iceServersHttpResponseParse(response);
                    }
                });
        httpConnection.send();
    }

    private void iceServersHttpResponseParse(String response) {
        Log.d(TAG, "Ice Servers response: " + response);
        try {
            JSONObject iceServersJson = new JSONObject(response);

            int result = iceServersJson.getInt("s");
            if (result != 200) {
                events.onIceServersError("Ice Servers response error: " + iceServersJson.getString("e"));
                return;
            }

            LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
            JSONArray iceServersArray = iceServersJson.getJSONObject("d").getJSONArray("iceServers");
            for (int i = 0; i < iceServersArray.length(); ++i) {

                String iceServerString = iceServersArray.getString(i);
                JSONObject iceServerJson = new JSONObject(iceServerString);

                String url = iceServerJson.getString("url");
                if (!this.turnEnabled && url.startsWith("turn:")) {
                    // if turn is not enabled and the server we got back is a turn (as opposed to stun), skip it
                    continue;
                }
                // username and credentials is optional, for example in the STUN server setting
                String username = "", password = "";
                if (iceServerJson.has("username")) {
                    username = iceServerJson.getString("username");
                }
                if (iceServerJson.has("credential")) {
                    password = iceServerJson.getString("credential");
                }
                iceServers.add(new PeerConnection.IceServer(url, username, password));

                Log.d(TAG, "==== URL: " + url + ", username: " + username + ", password: " + password);
            }

            events.onIceServersReady(iceServers);
        } catch (JSONException e) {
            events.onIceServersError("ICE server JSON parsing error: " + e.toString());
        }
    }

    // Requests & returns a TURN ICE Server based on a request URL.  Must be run
    // off the main thread!
    /*
    private LinkedList<PeerConnection.IceServer> requestTurnServers(String url)
            throws IOException, JSONException {
        LinkedList<PeerConnection.IceServer> turnServers =
                new LinkedList<PeerConnection.IceServer>();
        Log.d(TAG, "Request TURN from: " + url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TURN_HTTP_TIMEOUT_MS);
        connection.setReadTimeout(TURN_HTTP_TIMEOUT_MS);
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Non-200 response when requesting TURN server from "
                    + url + " : " + connection.getHeaderField(null));
        }
        InputStream responseStream = connection.getInputStream();
        String response = drainStream(responseStream);
        connection.disconnect();
        Log.d(TAG, "TURN response: " + response);
        JSONObject responseJSON = new JSONObject(response);
        String username = responseJSON.getString("username");
        String password = responseJSON.getString("password");
        JSONArray turnUris = responseJSON.getJSONArray("uris");
        for (int i = 0; i < turnUris.length(); i++) {
            String uri = turnUris.getString(i);
            turnServers.add(new PeerConnection.IceServer(uri, username, password));
        }
        return turnServers;
    }

    // Return the list of ICE servers described by a WebRTCPeerConnection
    // configuration string.
    private LinkedList<PeerConnection.IceServer> iceServersFromPCConfigJSON(
            String pcConfig) throws JSONException {
        JSONObject json = new JSONObject(pcConfig);
        JSONArray servers = json.getJSONArray("iceServers");
        LinkedList<PeerConnection.IceServer> ret =
                new LinkedList<PeerConnection.IceServer>();
        for (int i = 0; i < servers.length(); ++i) {
            JSONObject server = servers.getJSONObject(i);
            String url = server.getString("urls");
            String credential =
                    server.has("credential") ? server.getString("credential") : "";
            ret.add(new PeerConnection.IceServer(url, "", credential));
        }
        return ret;
    }

    // Return the contents of an InputStream as a String.
    private static String drainStream(InputStream in) {
        Scanner s = new Scanner(in).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
    */
}
