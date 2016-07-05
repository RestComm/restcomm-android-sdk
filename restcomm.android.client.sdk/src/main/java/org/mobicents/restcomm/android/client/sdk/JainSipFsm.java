package org.mobicents.restcomm.android.client.sdk;

import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.util.Arrays;
import java.util.HashMap;

public class JainSipFsm {
    String[] states;
    String id;
    String type;
    HashMap<String, Object> parameters;
    JainSipClient jainSipClient;
    static final String TAG = "JainSipFsm";
    int index;

    void init(String id, String type, JainSipClient jainSipClient, HashMap<String, Object> parameters) {
        this.id = id;
        this.type = type;
        this.jainSipClient = jainSipClient;
        this.parameters = parameters;
        index = 0;

        if (type.equals("close")) {
            states = new String[]{"shutdown"};
        } else if (type.equals("reconfigure")) {
            states = new String[]{"unbind", "bind", "register"};
        }
    }

    void init(String id, String type, JainSipClient jainSipClient) {
        this.init(id, type, jainSipClient, null);
    }

    void process(String id, String status) {
        if (this.id.equals(id)) {
            if (index >= states.length) {
                RCLogger.e(TAG, "process(): no more states to process");
            }
            if (type.equals("close")) {
                if (states[index].equals("shutdown")) {
                    if (status.equals("unregister-success")) {
                        jainSipClient.unbind();
                        jainSipClient.jainSipStopStack();
                        jainSipClient.listener.onClientClosedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                    } else if (status.equals("unregister-failure")) {
                        jainSipClient.unbind();
                        jainSipClient.jainSipStopStack();
                        jainSipClient.listener.onClientClosedEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_UNREGISTER_SERVICE_UNAVAILABLE,
                                RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNREGISTER_SERVICE_UNAVAILABLE));
                    }
                }
            } else if (type.equals("reconfigure")) {
                if (states[index].equals("unbind")) {
                    if (status.equals("unregister-success")) {
                        jainSipClient.unbind();
                    } else if (status.equals("unregister-failure")) {
                        // unregister step of reconfigure failed. Not that catastrophic, let's log it and continue; no need to notify UI thread just yet
                        RCLogger.w(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                        jainSipClient.unbind();
                    }
                } else if (states[index].equals("bind")) {
                    boolean secure = false;
                    if (parameters.containsKey("signaling-secure") && parameters.get("signaling-secure") == true) {
                        secure = true;
                    }
                    // TODO: fix static SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi and make dynamic based on current networking state
                    jainSipClient.bind(this.id, secure, SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi);
                } else if (states[index].equals("register")) {
                    jainSipClient.jainSipRegister(Long.toString(System.currentTimeMillis()), parameters, JainSipTransaction.RegistrationType.REGISTRATION_INITIAL);
                }

            }
            index++;
        }
    }
}
