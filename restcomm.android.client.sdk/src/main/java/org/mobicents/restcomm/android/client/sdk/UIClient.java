package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.util.HashMap;

// Client object that will send all asynchronous requests from UI towards signaling thread
public class UIClient {
    // handler at signaling thread to send messages to
    SignalingHandlerThread signalingHandlerThread;
    Handler signalingHandler;
    UIHandler uiHandler;
    Context context;

    public UIClient(UIClientListener listener, Context context)
    {
        uiHandler = new UIHandler(listener);
        this.context = context;

        signalingHandlerThread = new SignalingHandlerThread(uiHandler);
        signalingHandler = signalingHandlerThread.getHandler();

        ///// Defines a Handler object that's attached to the UI thread
        /*
        handler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                // Gets the image task from the incoming Message object.
                SipProfile profile = (SipProfile) inputMessage.obj;
            }
        };
        */
        /////
    }

    String open(HashMap<String,Object> parameters, boolean connectivity, SipManager.NetworkInterfaceType networkInterfaceType)
    {
        String id = generateId();
        SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.OPEN_REQUEST);
        signalingMessage.setParameters(parameters);
        signalingMessage.setAndroidContext(context);

        // TODO: remove these once reachability is properly handled (probably within the signaling thread)
        signalingMessage.connectivity = connectivity;
        signalingMessage.networkInterfaceType = networkInterfaceType;
        /*
        signalingMessage.setParametersOpen((String)parameters.get("pref_proxy_domain"),
                (String)parameters.get("pref_sip_user"),
                (String)parameters.get("pref_sip_password"));
                */
        Message message = signalingHandler.obtainMessage(1, signalingMessage);
        message.sendToTarget();

        return id;
    }

    String call(HashMap<String,String> parameters)
    {
        String id = generateId();
        SignalingMessage signalingMessage = new SignalingMessage(id, SignalingMessage.MessageType.CALL_REQUEST);
        // TODO: add message parameters
        Message message = signalingHandler.obtainMessage(1, signalingMessage);
        message.sendToTarget();

        return id;
    }

    String sendMessage(HashMap<String,String> parameters)
    {
        String id = generateId();

        // TODO: add handling

        return id;
    }

    void close()
    {

    }

    // Helpers

    // Generate unique identifier for 'transactions' created by UIClient, this can then be used as call-id when it enters JAIN SIP
    String generateId()
    {
        return Long.toString(System.currentTimeMillis());
    }
}
