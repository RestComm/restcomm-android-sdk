package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;
import android.javax.sip.ClientTransaction;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.InvalidArgumentException;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.PeerUnavailableException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.TransactionUnavailableException;
import android.javax.sip.TransportNotSupportedException;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.ContactHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.UserAgentHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.text.format.Formatter;

import org.apache.http.conn.util.InetAddressUtils;
import org.mobicents.restcomm.android.sipua.ISipEventListener;
import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.SipManagerState;
import org.mobicents.restcomm.android.sipua.impl.SecurityHelper;
import org.mobicents.restcomm.android.sipua.impl.SipManager;
import org.mobicents.restcomm.android.sipua.impl.sipmessages.Register;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.TooManyListenersException;

public class JainSipClient implements SipListener {
    JainSipClientListener listener;
    Context androidContext;
    private static final String TAG = "JainSipClient";

    // TODO: put this in a central place
    public static String USERAGENT_STRING = "TelScale Restcomm Android Client 1.0.0 BETA4";

    // JAIN SIP entities
    public SipFactory jainSipFactory;
    public SipStack jainSipStack;
    public ListeningPoint jainSipListeningPoint;
    public SipProvider jainSipProvider;
    public HeaderFactory jainSipHeaderFactory;
    public AddressFactory jainSipAddressFactory;
    public MessageFactory jainSipMessageFactory;
    public ContactHeader jainSipContactHeader;


    void open(String id, Context androidContext, HashMap<String, Object> parameters, JainSipClientListener listener, boolean connectivity, SipManager.NetworkInterfaceType networkInterfaceType)
    {
        this.listener = listener;
        this.androidContext = androidContext;

        jainSipFactory = SipFactory.getInstance();
        jainSipFactory.resetFactory();
        jainSipFactory.setPathName("android.gov.nist");

        Properties properties = new Properties();
        properties.setProperty("android.javax.sip.STACK_NAME", "androidSip");

        // Setup TLS even if currently we aren't using it, so that if user changes the setting later
        // the SIP stack is ready to support it
        //if (this.sipProfile.getTransport().equals("tls")) {
        // Generate custom keystore
        String keystoreFilename = "restcomm-android.keystore";
        HashMap<String, String> securityParameters = SecurityHelper.generateKeystore(androidContext, keystoreFilename);
        SecurityHelper.setProperties(properties, securityParameters.get("keystore-path"), securityParameters.get("keystore-password"));
        //}

        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        //properties.setProperty("android.gov.nist.javax.sip.TRACE_LEVEL", "32");
        //File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        //properties.setProperty("android.gov.nist.javax.sip.DEBUG_LOG", downloadPath.getAbsolutePath() + "/debug-jain.log");
        //properties.setProperty("android.gov.nist.javax.sip.SERVER_LOG", downloadPath.getAbsolutePath() + "/server-jain.log");

        try {
            /*
            if (jainSipListeningPoint != null) {
                // Binding again
                jainSipStack.deleteListeningPoint(jainSipListeningPoint);
                jainSipProvider.removeSipListener(this);
                jainSipListeningPoint = null;
            }
            */
            jainSipStack = jainSipFactory.createSipStack(properties);
            RCLogger.i(TAG, "createSipStack " + jainSipStack);
            jainSipHeaderFactory = jainSipFactory.createHeaderFactory();
            jainSipAddressFactory = jainSipFactory.createAddressFactory();
            jainSipMessageFactory = jainSipFactory.createMessageFactory();

            if (connectivity) {
                bind(networkInterfaceType);
            }

            // TODO: Didn't have that in the past, hope it doesn't cause any issues
            jainSipStack.start();

            //initialized = true;
        } catch (PeerUnavailableException e) {
            e.printStackTrace();
        } catch (SipException e) {
            e.printStackTrace();
        }

        if (parameters.containsKey("pref_proxy_domain") && !parameters.get("pref_proxy_domain").equals("")) {
            // Domain has been provided do the registration
            register(parameters);
        }
        else {
            // No Domain there we are done here
        }
    }

    void close(String id)
    {
    }

    void call(String id, HashMap<String, Object> parameters)
    {
    }

    void sendMessage(String id, String peer, String text)
    {

    }

    public void register(HashMap<String,Object> parameters)
    {
        RCLogger.v(TAG, "register()");
        int expiry = 3600;

        if (parameters.containsKey("register-expiry") && !parameters.get("register-expiry").equals("")) {
            expiry = (Integer)parameters.get("register-expiry");
        }

        /*
        if (jainSipProvider == null) {
            return;
        }
        */

        Register registerRequest = new Register();
        try {
            final Request r = registerRequest.MakeRequest(this, expiry, null, parameters);
            RCLogger.v(TAG, "Sending SIP request: \n" + r.toString());

            final SipProvider sipProvider = this.jainSipProvider;
            // Send the request statefully, through the client transaction.
            try {
                // remember that this might block waiting for DNS server
                ClientTransaction transaction = sipProvider.getNewClientTransaction(r);
                transaction.sendRequest();
            } catch (SipException e) {
                // DNS error (error resolving registrar URI)
                /*
                 dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_REGISTER_ERROR,
                    e.getMessage());
                 */
            }
        } catch (ParseException e) {
            // TODO: fix exception handling
            //throw e;
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        }
    }

    // setup JAIN networking facilities
    public void bind(SipManager.NetworkInterfaceType networkInterfaceType)
    {
        RCLogger.w(TAG, "bind()");
        if (jainSipListeningPoint == null) {
            // new network interface is up, let's retrieve its ip address
            //this.sipProfile.setLocalIp(getIPAddress(true, networkInterfaceType));
            try {
                jainSipListeningPoint = jainSipStack.createListeningPoint(
                        getIPAddress(true, networkInterfaceType), 5090,
                        "tls");
                jainSipProvider = jainSipStack.createSipProvider(jainSipListeningPoint);
                jainSipProvider.addSipListener(this);
            } catch (TransportNotSupportedException e) {
                e.printStackTrace();
            } catch (InvalidArgumentException e) {
                e.printStackTrace();
            } catch (ObjectInUseException e) {
                e.printStackTrace();
            } catch (TooManyListenersException e) {
                e.printStackTrace();
            }
        }
    }

    // TODO: Improve this, try to not depend on such low level facilities
    public String getIPAddress(boolean useIPv4, SipManager.NetworkInterfaceType networkInterfaceType)
    {
        RCLogger.i(TAG, "getIPAddress()");
        try {
            if (networkInterfaceType == SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi) {
                WifiManager wifiMgr = (WifiManager) androidContext.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                return Formatter.formatIpAddress(ip);
            }

            if (networkInterfaceType == SipManager.NetworkInterfaceType.NetworkInterfaceTypeCellularData) {
                List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface intf : interfaces) {
                    if (!intf.getName().matches("wlan.*")) {
                        List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                        for (InetAddress addr : addrs) {
                            if (!addr.isLoopbackAddress()) {
                                String sAddr = addr.getHostAddress().toUpperCase();
                                boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                                if (useIPv4) {
                                    if (isIPv4)
                                        return sAddr;
                                } else {
                                    if (!isIPv4) {
                                        int delim = sAddr.indexOf('%'); // drop ip6 port
                                        // suffix
                                        return delim < 0 ? sAddr : sAddr.substring(0,
                                                delim);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // TODO: properly handle exception
    public UserAgentHeader generateUserAgentHeader()
    {
        RCLogger.i(TAG, "generateUserAgentHeader()");
        List<String> userAgentTokens = new LinkedList<String>();
        UserAgentHeader header = null;
        userAgentTokens.add(USERAGENT_STRING);
        try {
            header = this.jainSipHeaderFactory.createUserAgentHeader(userAgentTokens);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return header;
    }

    // -- SipListener events
    public void processRequest(RequestEvent requestEvent)
    {

    }

    public void processResponse(ResponseEvent responseEvent)
    {

    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent)
    {
        /*
                RCLogger.i(TAG, "SipManager.processDialogTerminated: " + dialogTerminatedEvent.toString() + "\n" +
                        "\tdialog: " + dialogTerminatedEvent.getDialog().toString());
        */
    }

    public void processIOException(IOExceptionEvent exceptionEvent)
    {
        /*
                RCLogger.e(TAG, "SipManager.processIOException: " + exceptionEvent.toString() + "\n" +
                        "\thost: " + exceptionEvent.getHost() + "\n" +
                        "\tport: " + exceptionEvent.getPort());
        */
    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent)
    {
        /*
                RCLogger.i(TAG, "SipManager.processTransactionTerminated: " + transactionTerminatedEvent.toString() + "\n" +
                        "\tclient transaction: " + transactionTerminatedEvent.getClientTransaction() + "\n" +
                        "\tserver transaction: " + transactionTerminatedEvent.getServerTransaction() + "\n" +
                        "\tisServerTransaction: " + transactionTerminatedEvent.isServerTransaction());
        */
    }

    public void processTimeout(TimeoutEvent timeoutEvent)
    {
        /*
        Request request;
        if (timeoutEvent.isServerTransaction()) {
            request = timeoutEvent.getServerTransaction().getRequest();
        }
        else {
            request = timeoutEvent.getClientTransaction().getRequest();
        }
        RCLogger.i(TAG, "processTimeout(): method: " + request.getMethod() + " URI: " + request.getRequestURI());
        if (request.getMethod() == Request.INVITE) {
            dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_CALL, RCClient.ErrorCodes.SIGNALLING_TIMEOUT,
                    "Timed out waiting on " + request.getMethod());
        }
        else {
            dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_TIMEOUT,
                    "Timed out waiting on " + request.getMethod());
        }
        */
    }

    // -- Helpers

    // convert sip uri, like  sip:cloud.restcomm.com:5060 -> cloud.restcomm.com
    public String sipUri2IpAddress(String sipUri) throws ParseException {
        Address address = jainSipAddressFactory.createAddress(sipUri);
        return ((SipURI)address.getURI()).getHost();
    }

}
