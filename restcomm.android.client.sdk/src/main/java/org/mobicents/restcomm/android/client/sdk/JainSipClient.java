package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;
import android.gov.nist.javax.sip.ResponseEventExt;
import android.gov.nist.javax.sip.SipStackExt;
import android.gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import android.javax.sip.ClientTransaction;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.ListeningPoint;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.address.Address;
import android.javax.sip.address.AddressFactory;
import android.javax.sip.address.SipURI;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.header.UserAgentHeader;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.text.format.Formatter;

import org.apache.http.conn.util.InetAddressUtils;
import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.impl.AccountManagerImpl;
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

public class JainSipClient implements SipListener {
    JainSipClientListener listener;
    JainSipMessageBuilder jainSipMessageBuilder;
    Context androidContext;
    private static final String TAG = "JainSipClient";

    // JAIN SIP entities
    public SipFactory jainSipFactory;
    public SipStack jainSipStack;
    public ListeningPoint jainSipListeningPoint;
    public SipProvider jainSipProvider;

    JainSipTransactionManager jainSipTransactionManager;

    void open(String id, Context androidContext, HashMap<String, Object> parameters, JainSipClientListener listener, boolean connectivity, SipManager.NetworkInterfaceType networkInterfaceType)
    {
        this.listener = listener;
        this.androidContext = androidContext;
        jainSipMessageBuilder = new JainSipMessageBuilder();
        jainSipTransactionManager = new JainSipTransactionManager();

        jainSipFactory = SipFactory.getInstance();
        jainSipFactory.resetFactory();
        jainSipFactory.setPathName("android.gov.nist");

        Properties properties = new Properties();
        properties.setProperty("android.javax.sip.STACK_NAME", "androidSip");

        boolean secure = false;
        if (parameters.containsKey("signaling-secure") && parameters.get("signaling-secure") == true) {
            secure = true;
        }

        // Setup TLS even if currently we aren't using it, so that if user changes the setting later
        // the SIP stack is ready to support it
        String keystoreFilename = "restcomm-android.keystore";
        HashMap<String, String> securityParameters = SecurityHelper.generateKeystore(androidContext, keystoreFilename);
        SecurityHelper.setProperties(properties, securityParameters.get("keystore-path"), securityParameters.get("keystore-password"));

        if (parameters.containsKey("jain-sip-logging-enabled") && parameters.get("jain-sip-logging-enabled") == true) {
            // You need 16 for logging traces. 32 for debug + traces.
            // Your code will limp at 32 but it is best for debugging.
            properties.setProperty("android.gov.nist.javax.sip.TRACE_LEVEL", "32");
            File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            //properties.setProperty("android.gov.nist.javax.sip.DEBUG_LOG", downloadPath.getAbsolutePath() + "/debug-jain.log");
            //properties.setProperty("android.gov.nist.javax.sip.SERVER_LOG", downloadPath.getAbsolutePath() + "/server-jain.log");
        }

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
            //RCLogger.i(TAG, "createSipStack " + jainSipStack);
            jainSipMessageBuilder.jainSipHeaderFactory = jainSipFactory.createHeaderFactory();
            jainSipMessageBuilder.jainSipAddressFactory = jainSipFactory.createAddressFactory();
            jainSipMessageBuilder.jainSipMessageFactory = jainSipFactory.createMessageFactory();

            if (connectivity) {
                bind(id, secure, networkInterfaceType);
            }

            // TODO: Didn't have that in the past, hope it doesn't cause any issues
            jainSipStack.start();

            //initialized = true;
        }  catch (SipException e) {
            listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP));
            RCLogger.e(TAG, "open(): " + e.getMessage());
            e.printStackTrace();
        }

        if (parameters.containsKey("pref_proxy_domain") && !parameters.get("pref_proxy_domain").equals("")) {
            // Domain has been provided do the registration
            register(id, parameters);
        }
        else {
            // No Domain there we are done here
            listener.onClientOpenedEvent(id);
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

    public void register(String id, HashMap<String,Object> parameters)
    {
        RCLogger.v(TAG, "register()");
        int expiry = 3600;

        if (parameters.containsKey("register-expiry") && !parameters.get("register-expiry").equals("")) {
            expiry = (Integer)parameters.get("register-expiry");
        }

        try {
            Request registerRequest = jainSipMessageBuilder.buildRegister(id, listener, jainSipListeningPoint, expiry, null, parameters);
            if (registerRequest != null) {
                RCLogger.v(TAG, "Sending SIP request: \n" + registerRequest.toString());

                // Send the request statefully, through the client transaction. Remember that this might block waiting for DNS server
                ClientTransaction transaction = this.jainSipProvider.getNewClientTransaction(registerRequest);
                transaction.sendRequest();

                jainSipTransactionManager.add(id, JainSipTransaction.Type.TYPE_REGISTRATION, transaction, parameters);
            }
        }
        catch (SipException e) {
            // android.javax.sip.SipException: Could not connect to cloud.restcomm.com/107.21.247.251:5070
            // TODO: DNS error (error resolving registrar URI)
            /*
            dispatchSipError(ISipEventListener.ErrorContext.ERROR_CONTEXT_NON_CALL, RCClient.ErrorCodes.SIGNALLING_REGISTER_ERROR,
                e.getMessage());
             */
            listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT));
            RCLogger.e(TAG, "register(): Error parsing REGISTER SIP URI: " + e.getMessage());
            e.printStackTrace();
        }
        /*
        catch (Exception e) {
            listener.onClientErrorEvent(id);
            RCLogger.e(TAG, "register(): " + e.getMessage());
            e.printStackTrace();
        }
        */
        /*
        catch (ParseException e) {
            //e.printStackTrace();
            listener.onClientOpenedEvent(id);
            RCLogger.i(TAG, "register(): " + e.getMessage());
        }
        catch (InvalidArgumentException e) {
            //e.printStackTrace();
            listener.onClientOpenedEvent(id);
            RCLogger.i(TAG, "register(): " + e.getMessage());
        }
        */
    }

    // setup JAIN networking facilities
    public void bind(String id, boolean secure, SipManager.NetworkInterfaceType networkInterfaceType)
    {
        RCLogger.w(TAG, "bind()");
        if (jainSipListeningPoint == null) {
            // new network interface is up, let's retrieve its ip address
            String transport = "tcp";
            if (secure) {
                transport = "tls";
            }
            try {
                jainSipListeningPoint = jainSipStack.createListeningPoint(
                        getIPAddress(id, true, networkInterfaceType), 5090,
                        transport);
                jainSipProvider = jainSipStack.createSipProvider(jainSipListeningPoint);
                jainSipProvider.addSipListener(this);
            }
            catch (Exception e) {
                listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_BINDING, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_BINDING));
                RCLogger.e(TAG, "register(): " + e.getMessage());
                e.printStackTrace();
            }
            /*
            catch (TransportNotSupportedException e) {
                e.printStackTrace();
            }
            catch (InvalidArgumentException e) {
                e.printStackTrace();
            }
            catch (ObjectInUseException e) {
                e.printStackTrace();
            }
            catch (TooManyListenersException e) {
                e.printStackTrace();
            }
            */
        }
    }

    // -- SipListener events
    public void processRequest(RequestEvent requestEvent)
    {

    }

    public void processResponse(ResponseEvent responseEvent)
    {
        ResponseEventExt responseEventExt = (ResponseEventExt)responseEvent;
        Response response = (Response) responseEvent.getResponse();
        RCLogger.v(TAG, "Received SIP response: \n" + response.toString());

        CallIdHeader callIdHeader = (CallIdHeader)response.getHeader("Call-ID");
        String callId = callIdHeader.getCallId();
        JainSipTransaction jainSipTransaction = jainSipTransactionManager.get(callId);
        if (jainSipTransaction == null) {
            RCLogger.e(TAG, "processResponse(): warning, got response for unknown transaction");
            return;
        }

        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        String method = cseq.getMethod();
        if (method.equals(Request.REGISTER)) {
            if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED || response.getStatusCode() == Response.UNAUTHORIZED) {
                try {
                    AuthenticationHelper authenticationHelper = ((SipStackExt) jainSipStack).getAuthenticationHelper(
                            new AccountManagerImpl((String) jainSipTransaction.parameters.get("pref_sip_user"),
                                    responseEventExt.getRemoteIpAddress(), (String) jainSipTransaction.parameters.get("pref_sip_password")), jainSipMessageBuilder.jainSipHeaderFactory);

                    // we 're subtracting one since the first attempt has already taken place
                    // (that way we are enforcing MAX_AUTH_ATTEMPTS at most)
                    if (jainSipTransaction.shouldRetry()) {
                        ClientTransaction authenticationTransaction = authenticationHelper.handleChallenge(response, (ClientTransaction) jainSipTransaction.transaction, jainSipProvider, 5, true);

                        // update previous transaction with authenticationTransaction (remember that previous ended with 407 final response)
                        jainSipTransaction.update(authenticationTransaction);
                        RCLogger.v(TAG, "Sending SIP request: \n" + authenticationTransaction.getRequest().toString());
                        authenticationTransaction.sendRequest();
                        jainSipTransaction.increaseAuthAttempts();
                    } else {
                        jainSipTransactionManager.remove(callId);
                        listener.onClientErrorEvent(callId, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_MAX_RETRIES_REACHED,
                                RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_MAX_RETRIES_REACHED));
                    }
                } catch (Exception e) {
                    jainSipTransactionManager.remove(callId);
                    listener.onClientErrorEvent(callId, RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
                    RCLogger.e(TAG, "register(): " + e.getMessage());
                    e.printStackTrace();
                }
            /*
            catch (NullPointerException e) {
                e.printStackTrace();
            }
            catch (SipException e) {
                e.printStackTrace();
            }
            */
            } else if (response.getStatusCode() == Response.FORBIDDEN) {
                jainSipTransactionManager.remove(callId);
                listener.onClientErrorEvent(callId, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_FORBIDDEN, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_FORBIDDEN));
            } else if (response.getStatusCode() == Response.OK) {
                // register succeeded
                jainSipTransactionManager.remove(callId);
                listener.onClientOpenedEvent(jainSipTransaction.id);
            }
        }
        else if (method.equals(Request.INVITE)) {
            // TODO: forward to JainSipCall for processing

        }
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
        Request request;
        if (timeoutEvent.isServerTransaction()) {
            request = timeoutEvent.getServerTransaction().getRequest();
        }
        else {
            request = timeoutEvent.getClientTransaction().getRequest();
        }

        RCLogger.i(TAG, "processTimeout(): method: " + request.getMethod() + " URI: " + request.getRequestURI());
        CallIdHeader callIdHeader = (CallIdHeader)request.getHeader("Call-ID");
        String callId = callIdHeader.getCallId();
        JainSipTransaction jainSipTransaction = jainSipTransactionManager.get(callId);
        if (jainSipTransaction == null) {
            // transaction is not identified, just emit a log error; don't notify UI thread
            RCLogger.i(TAG, "processTimeout(): transaction not identified");
            return;
        }

        if (jainSipTransaction.type == JainSipTransaction.Type.TYPE_REGISTRATION) {
            // TODO: need to handle registration refreshes
            jainSipTransactionManager.remove(callId);
            listener.onClientErrorEvent(callId, RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_TIMEOUT, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_TIMEOUT));
        }
        else if (jainSipTransaction.type == JainSipTransaction.Type.TYPE_CALL) {
            // TODO: call JainSipCall.processTimeout()
        }
        else if (jainSipTransaction.type == JainSipTransaction.Type.TYPE_MESSAGE) {
            // TODO: call JainSipMessage.processTimeout()
        }

        /*
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

    // TODO: Improve this, try to not depend on such low level facilities
    public String getIPAddress(String id, boolean useIPv4, SipManager.NetworkInterfaceType networkInterfaceType)
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
            //listener.onClientErrorEvent(id);
            listener.onClientErrorEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_INTERFACE, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_INTERFACE));
            RCLogger.i(TAG, "register(): " + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }

}
