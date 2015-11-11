package org.mobicents.restcomm.android.sipua.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.util.HashMap;

import org.mobicents.restcomm.android.client.sdk.RCClient;
import org.mobicents.restcomm.android.sipua.IDevice;
import org.mobicents.restcomm.android.sipua.NotInitializedException;
import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.SipUAConnectionListener;
import org.mobicents.restcomm.android.sipua.SipUADeviceListener;
import org.mobicents.restcomm.android.sipua.impl.SipEvent.SipEventType;
import org.mobicents.restcomm.android.sipua.RCLogger;

import android.content.Context;
import android.javax.sip.TransactionUnavailableException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.util.Log;

public class DeviceImpl implements IDevice,Serializable {
	
	private static DeviceImpl device;
	Context context;
	SipManager sipManager;
	SipProfile sipProfile;
	public SoundManager soundManager;
	private static boolean initialized = false;
	private int registrationExpiry = 3600;
	private int registrationRefresh = 60;
	public SipUADeviceListener sipuaDeviceListener = null;
	public SipUAConnectionListener sipuaConnectionListener = null;
	private static final String TAG = "DeviceImpl";
	// use this handler for registration refreshes
	Handler registerRefreshHandler = null;

	public enum ReachabilityState {
		REACHABILITY_WIFI,
		REACHABILITY_MOBILE,
		REACHABILITY_NONE,
	}

	private DeviceImpl(){
		
	}
	public static DeviceImpl GetInstance(){
		if (device == null){
			RCLogger.v(TAG, "Getting allocated");
			device = new DeviceImpl();
		}
		return device;
	}
    public void Initialize(Context context, SipProfile sipProfile, boolean connectivity, HashMap<String,String> customHeaders){
        this.Initialize(context, sipProfile, connectivity);
        sipManager.setCustomHeaders(customHeaders);
    }
	public void Initialize(Context context, SipProfile sipProfile, boolean connectivity) {
		RCLogger.v(TAG, "Initialize()");

		this.context = context;
		this.sipProfile = sipProfile;
		sipManager = new SipManager(sipProfile, connectivity);
		soundManager = new SoundManager(context,sipProfile.getLocalIp());
		sipManager.addSipListener(this);
		registerRefreshHandler = new Handler(context.getMainLooper());
		initialized = true;
	}

	// release JAIN networking facilities
	public void unbind()
	{
		// disable auto registration refreshes
		registerRefreshHandler.removeCallbacksAndMessages(null);
		sipManager.unbind();
	}

	// setup JAIN networking facilities
	public void bind()
	{
		sipManager.bind();
	}

	public void Shutdown()
	{
		RCLogger.v(TAG, "Shutdown");
		if (initialized) {
			RCLogger.v(TAG, "Shutdown while initialized");
			sipManager.removeSipListener(this);
			sipManager.shutdown();
			sipuaDeviceListener = null;
			sipuaConnectionListener = null;
			registerRefreshHandler.removeCallbacksAndMessages(null);
			registerRefreshHandler = null;

			// mark the instace null so that it gets freed
			device = null;
			initialized = false;
		}
	}

	public static boolean isInitialized()
	{
		return initialized;
	}

	@Override
	public void onSipMessage(final SipEvent sipEventObject) {
		RCLogger.i(TAG, "onSipMessage");
		if (sipEventObject.type == SipEventType.MESSAGE) {
			if (this.sipuaDeviceListener != null) {
				this.sipuaDeviceListener.onSipUAMessageArrived(new SipEvent(this, SipEvent.SipEventType.MESSAGE, sipEventObject.content, sipEventObject.from));
				soundManager.incomingMessage();
			}
		} else if (sipEventObject.type == SipEventType.INCOMING_BYE_REQUEST ||
				sipEventObject.type == SipEventType.INCOMING_BYE_RESPONSE) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are disconnected (either when we get a response to our BYE, or when we receive a BYE request
				this.sipuaConnectionListener.onSipUADisconnected(sipEventObject);
			}
		} else if (sipEventObject.type == SipEventType.REMOTE_CANCEL) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are connected
				this.sipuaConnectionListener.onSipUACancelled(null);
				soundManager.stopRinging();
			}
		} else if (sipEventObject.type == SipEventType.DECLINED) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are connected
				this.sipuaConnectionListener.onSipUADeclined(null);
				soundManager.stopCalling();
			}
		}else if (sipEventObject.type == SipEventType.BUSY_HERE) {
			soundManager.stopCalling();
		} else if (sipEventObject.type == SipEventType.SERVICE_UNAVAILABLE) {
			soundManager.stopCalling();
		} else if (sipEventObject.type == SipEventType.CALL_CONNECTED) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are connected
				this.sipuaConnectionListener.onSipUAConnected(sipEventObject);
				soundManager.stopRinging();
				soundManager.stopCalling();
			}
		} else if (sipEventObject.type == SipEventType.REMOTE_RINGING) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are connecting
				this.sipuaConnectionListener.onSipUAConnecting(null);
				soundManager.startCalling();
			}
		} else if (sipEventObject.type == SipEventType.LOCAL_RINGING) {
			if (this.sipuaDeviceListener != null) {
				this.sipuaDeviceListener.onSipUAConnectionArrived(sipEventObject);
				soundManager.startRinging();
			}
		} else if (sipEventObject.type == SipEventType.NOT_FOUND) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are connecting
				this.sipuaConnectionListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_DESTINATION_NOT_FOUND, "Destination not found");
			}
		}
	}

	public void onSipError(ErrorContext errorContext, RCClient.ErrorCodes errorCode, String errorText)
	{
		if (errorContext == ErrorContext.ERROR_CONTEXT_NON_CALL &&  this.sipuaDeviceListener != null) {
			// this is a signalling error outside a call; we need to notify RCDevice
			this.sipuaDeviceListener.onSipUAError(errorCode, errorText);
		}
		if (errorContext == ErrorContext.ERROR_CONTEXT_CALL && this.sipuaConnectionListener != null) {
			// this is a signalling error inside a call; we need to notify RCConnection
			this.sipuaConnectionListener.onSipUAError(errorCode, errorText);
		}
	}

	@Override
	public void Call(String to,  HashMap<String, String> sipHeaders) {
		RCLogger.i(TAG, "Call(): " + to);
		if (sipHeaders != null) {
			RCLogger.i(TAG, "Call(): " + sipHeaders.toString());
		}
		try {
			this.sipManager.Call(to, 0, sipHeaders);
		} catch (NotInitializedException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are connecting
				this.sipuaConnectionListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		}
	}

	public void CallWebrtc(String to, String sdp, HashMap<String, String> sipHeaders) {
		RCLogger.i(TAG, "CallWebrtc(): " + to);
		if (sipHeaders != null) {
			RCLogger.i(TAG, "CallWebrtc(): " + sipHeaders.toString());
		}
		try {
			if (checkReachability(this.context) != ReachabilityState.REACHABILITY_NONE) {
				this.sipManager.CallWebrtc(to, sdp, sipHeaders);
			}
			else {
				RCLogger.e(TAG, "No reachability");
			}
		} catch (NotInitializedException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			if (this.sipuaConnectionListener != null) {
				// notify our listener that we are connecting
				this.sipuaConnectionListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		}
	}

	@Override
	public void Accept() {
		RCLogger.i(TAG, "Accept()");

		sipManager.AcceptCall(0);
		soundManager.stopRinging();
	}

	public void AcceptWebrtc(final String sdp) {
		RCLogger.i(TAG, "AcceptWebrtc()");

		sipManager.AcceptCallWebrtc(sdp);
		soundManager.stopRinging();
	}

	@Override
	public void Reject() {
		RCLogger.i(TAG, "Reject()");

		sipManager.RejectCall();
		soundManager.stopRinging();
	}

	@Override
	public void Cancel() {
		RCLogger.i(TAG, "Cancel()");

		try {
			sipManager.Cancel();
		} catch (NotInitializedException e) {
			e.printStackTrace();
		}
		soundManager.stopCalling();
	}

	@Override
	public void Hangup() {
		RCLogger.i(TAG, "Hangup()");

		if (this.sipManager.direction == this.sipManager.direction.OUTGOING ||
				this.sipManager.direction == this.sipManager.direction.INCOMING) {
			try {
				this.sipManager.Hangup();
			} catch (NotInitializedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void SendMessage(String to, String message) {
		RCLogger.i(TAG, "SendMessage(): " + to + "\nmessage: " + message);

		try {
			this.sipManager.SendMessage(to, message);
		} catch (NotInitializedException e) {
			e.printStackTrace();
		}
		soundManager.outgoingMessage();
	}
	@Override
	public void SendDTMF(String digit) {
		RCLogger.i(TAG, "SendDTMF(): " + digit);
		try {
			this.sipManager.SendDTMF(digit);
		} catch (NotInitializedException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void Register() {
		RCLogger.v(TAG, "Register");
		if (sipProfile.getRemoteEndpoint().isEmpty()) {
			// registrarless mode, skip registration
			return;
		}

		try {
			this.sipManager.Register(registrationExpiry);
		} catch (TransactionUnavailableException e) {
			if (this.sipuaDeviceListener != null) {
				// notify our listener that we are connecting
				this.sipuaDeviceListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		} catch (ParseException e) {
			if (this.sipuaDeviceListener != null) {
				// notify our listener that we are connecting
				this.sipuaDeviceListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		}

		if (registerRefreshHandler != null) {
			// if this is an on-demand registration (as opposed to scheduled) we need
			// to cancel any pending scheduled registrations
			registerRefreshHandler.removeCallbacksAndMessages(null);
		}

		// schedule a registration update after 'registrationRefresh' seconds
		Runnable myRunnable = new Runnable() {
			@Override
			public void run() {
				Register();
			}
		};
		registerRefreshHandler.postDelayed(myRunnable, registrationRefresh * 1000);

	}

	public void RefreshNetworking()
	{
		RCLogger.v(TAG, "RefreshNetworking");

		try {
			this.sipManager.refreshNetworking(registrationExpiry);
		} catch (TransactionUnavailableException e) {
			if (this.sipuaDeviceListener != null) {
				// notify our listener that we are connecting
				this.sipuaDeviceListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		} catch (ParseException e) {
			if (this.sipuaDeviceListener != null) {
				// notify our listener that we are connecting
				this.sipuaDeviceListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		}
	}

	public void Unregister() {
		RCLogger.v(TAG, "Unregister");

		if (sipProfile.getRemoteEndpoint().isEmpty()) {
			// registrarless mode, skip unregistration
			return;
		}

		if (registerRefreshHandler != null) {
			// we are unregistering, stop future registrations
			registerRefreshHandler.removeCallbacksAndMessages(null);
		}

		try {
			this.sipManager.Unregister(null);
		} catch (TransactionUnavailableException e) {
			if (this.sipuaDeviceListener != null) {
				// notify our listener that we are connecting
				this.sipuaDeviceListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		} catch (ParseException e) {
			if (this.sipuaDeviceListener != null) {
				// notify our listener that we are connecting
				this.sipuaDeviceListener.onSipUAError(RCClient.ErrorCodes.SIGNALLING_SIPURI_PARSE_ERROR, "Error parsing SIP URI");
			}
		}
	}

	@Override
	public SipManager GetSipManager() {
		// TODO Auto-generated method stub
		return sipManager;
	}

	@Override
	public void Mute(boolean muted)
	{
	}

	@Override
	public SoundManager getSoundManager() {
		// TODO Auto-generated method stub
		return soundManager;
	}
	public static byte[] serialize(Object o) { 
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();  
	    try {   
	        ObjectOutput out = new ObjectOutputStream(bos);      
	        out.writeObject(o);                                       //This is where the Exception occurs
	        out.close();     
	        // Get the bytes of the serialized object    
	        byte[] buf = bos.toByteArray();   
	        return buf;    
	    } catch(IOException ioe) { 
	        return null;
	    }  

	}

	public static Object deserialize(byte[] b) {  
	        try {    
	            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(b));    
	            Object object = in.readObject();    
	            in.close();  
	            return object;  
	        } catch(ClassNotFoundException cnfe) {
	            return null;
	        } catch(IOException ioe) {  
	            return null;
	        } 
	    }

	static public ReachabilityState checkReachability(Context context)
	{
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		if (null != activeNetwork) {
			if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && activeNetwork.isConnected()) {
				RCLogger.w(TAG, "Reachability event: WIFI");
				return ReachabilityState.REACHABILITY_WIFI;
			}

			// TODO: leave mobile internet out until we fix some issues that came up; check https://github.com/Mobicents/restcomm-android-sdk/issues/172
			/*
			if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE && activeNetwork.isConnected()) {
				Log.w(TAG, "Reachability event: MOBILE");
				return ReachabilityState.REACHABILITY_MOBILE;
			}
			*/
		}
		RCLogger.w(TAG, "Reachability event: NONE");
		return ReachabilityState.REACHABILITY_NONE;
	}




}
