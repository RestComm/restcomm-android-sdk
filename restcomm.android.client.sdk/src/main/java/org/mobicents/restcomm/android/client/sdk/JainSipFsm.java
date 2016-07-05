package org.mobicents.restcomm.android.client.sdk;

import org.mobicents.restcomm.android.sipua.RCLogger;

public class JainSipFsm {
    String[] states;
    String id;
    String type;
    JainSipClient jainSipClient;
    static final String TAG = "JainSipFsm";
    int index;

    void init(String id, String type, JainSipClient jainSipClient)
    {
        this.id = id;
        this.type = type;
        this.jainSipClient = jainSipClient;
        index = 0;

        if (type.equals("close")) {
            states = new String[] { "shutdown" };
        }
    }

    void process(String id, String status)
    {
        if (this.id.equals(id)) {
            if (index >= states.length) {
                RCLogger.e(TAG, "process(): no more states to process");
            }
            if (states[index].equals("shutdown")) {
                if (status.equals("unregister-success")) {
                    jainSipClient.jainSipStopStack();
                    jainSipClient.listener.onClientClosedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                }
                if (status.equals("unregister-failure")) {
                    jainSipClient.jainSipStopStack();
                    jainSipClient.listener.onClientClosedEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_UNREGISTER_SERVICE_UNAVAILABLE,
                            RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNREGISTER_SERVICE_UNAVAILABLE));
                }
            }
            index++;
        }
    }
}
