package org.mobicents.restcomm.android.sipua.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

import org.mobicents.restcomm.android.sipua.IDevice;
import org.mobicents.restcomm.android.sipua.NotInitializedException;
import org.mobicents.restcomm.android.sipua.SipProfile;
import org.mobicents.restcomm.android.sipua.impl.SipEvent.SipEventType;
// ISSUE#17: commented those, as we need to decouple the UI details
//import org.mobicents.restcomm.android.sdk.ui.IncomingCall;
//import org.mobicents.restcomm.android.sdk.ui.NotifyMessage;

import android.content.Context;

public class DeviceImpl implements IDevice,Serializable {
	
	private static DeviceImpl device;
	Context context;
	SipManager sipManager;
	SipProfile sipProfile;
	SoundManager soundManager;
	boolean isInitialized;
	private DeviceImpl(){
		
	}
	public static DeviceImpl GetInstance(){
		if(device == null){
			device = new DeviceImpl();
		}
		return device;
	}
    public void Initialize(Context context, SipProfile sipProfile, HashMap<String,String> customHeaders){
        this.Initialize(context,sipProfile);
        sipManager.setCustomHeaders(customHeaders);
    }
	public void Initialize(Context context, SipProfile sipProfile){
		this.context = context;
		this.sipProfile = sipProfile;
		sipManager = new SipManager(sipProfile);
		soundManager = new SoundManager(context,sipProfile.getLocalIp());
		sipManager.addSipListener(this);
	}
	
	@Override
	public void onSipMessage(final SipEvent sipEventObject) {
		System.out.println("Sip Event fired");
		if (sipEventObject.type == SipEventType.MESSAGE) {
			/*chatText += sipEventObject.from + ":" + sipEventObject.content
					+ "\r\n";
			this.runOnUiThread(new Runnable() {
				public void run() {
					textViewChat.append(chatText);

				}
			});*/
		} else if (sipEventObject.type == SipEventType.BYE) {
			this.soundManager.releaseAudioResources();
		} else if (sipEventObject.type == SipEventType.DECLINED) {
			this.soundManager.releaseAudioResources();
		}else if (sipEventObject.type == SipEventType.BUSY_HERE) {
			this.soundManager.releaseAudioResources();
		} else if (sipEventObject.type == SipEventType.SERVICE_UNAVAILABLE) {
			this.soundManager.releaseAudioResources();

		} else if (sipEventObject.type == SipEventType.CALL_CONNECTED) {
			this.soundManager.setupAudio(sipEventObject.remoteRtpPort, this.sipProfile.getRemoteIp());
			// ISSUE#17: commented those, as we need to decouple the UI details
			//createNotif();
		} else if (sipEventObject.type == SipEventType.LOCAL_RINGING) {
			// ISSUE#17: commented those, as we need to decouple the UI details
			/*
			Intent i = new Intent(context, IncomingCall.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			
			context.startActivity(i);
			*/
			// TODO: notify that local side is ringing

			/*Handler handler = new Handler(context.getMainLooper());
			handler.post(new Runnable() {
				public void run() {
					AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
							((Activity)context));

					// set title
					alertDialogBuilder.setTitle("Incoming call from:" + sipEventObject.from);

					// set dialog message
					alertDialogBuilder
							.setCancelable(false)
							.setPositiveButton("Accept",
									new DialogInterface.OnClickListener() {
										public void onClick(
												DialogInterface dialog, int id) {
											try {

												sipManager
														.AcceptCall(
																soundManager.setupAudioStream(sipProfile.getLocalIp()));
											} catch (SdpException e) {
												e.printStackTrace();
											}
										}
									})
							.setNegativeButton("Reject",
									new DialogInterface.OnClickListener() {
										public void onClick(
												DialogInterface dialog, int id) {
											sipManager
													.RejectCall();
											dialog.cancel();
										}
									});

				
					AlertDialog alertDialog = alertDialogBuilder.create();

				
					alertDialog.show();

				}
			});*/
		}
	}
	// ISSUE#17: commented those, as we need to decouple the UI details
	/*
	private void createNotif() {
		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(context)
		        .setSmallIcon(R.drawable.dial)
		        .setContentTitle("My notification")
		        .setContentText("Hello World!")
		        .setPriority(Notification.PRIORITY_MAX)
		        .setDefaults(Notification.DEFAULT_VIBRATE);
		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = new Intent(context, NotifyMessage.class);

		// The stack builder object will contain an artificial back stack for the
		// started Activity.
		// This ensures that navigating backward from the Activity leads out of
		// your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(NotifyMessage.class);
		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent =
		        stackBuilder.getPendingIntent(
		            0,
		            PendingIntent.FLAG_UPDATE_CURRENT
		        );
		mBuilder.setContentIntent(resultPendingIntent);
		NotificationManager mNotificationManager =
		    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		mNotificationManager.notify(1, mBuilder.build());	
		}
		*/
	@Override
	public void Call(String to) {
		try {
			this.sipManager.Call(to,this.soundManager.setupAudioStream(sipProfile.getLocalIp()));
		} catch (NotInitializedException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void SendMessage(String to, String message) {
		try {
			this.sipManager.SendMessage(to, message);
		} catch (NotInitializedException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void SendDTMF(String digit) {
		try {
			this.sipManager.SendDTMF(digit);
		} catch (NotInitializedException e) {
			e.printStackTrace();
		}

	}
	@Override
	public void Register() {
		this.sipManager.Register();
	}





	@Override
	public SipManager GetSipManager() {
		// TODO Auto-generated method stub
		return sipManager;
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
	        //Log.e("serializeObject", "error", ioe);           //"ioe" says java.io.NotSerializableException exception
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
	            //Log.e("deserializeObject", "class not found error", cnfe);   
	            return null;  
	        } catch(IOException ioe) {  
	            //Log.e("deserializeObject", "io error", ioe);    
	            return null; 
	        } 
	    } 
	
	

}
