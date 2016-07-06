package org.mobicents.restcomm.android.client.sdk;

import android.content.Context;
import android.gov.nist.javax.sip.ResponseEventExt;
import android.gov.nist.javax.sip.SipStackExt;
import android.gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import android.javax.sip.ClientTransaction;
import android.javax.sip.DialogTerminatedEvent;
import android.javax.sip.IOExceptionEvent;
import android.javax.sip.ListeningPoint;
import android.javax.sip.ObjectInUseException;
import android.javax.sip.RequestEvent;
import android.javax.sip.ResponseEvent;
import android.javax.sip.SipException;
import android.javax.sip.SipFactory;
import android.javax.sip.SipListener;
import android.javax.sip.SipProvider;
import android.javax.sip.SipStack;
import android.javax.sip.TimeoutEvent;
import android.javax.sip.TransactionTerminatedEvent;
import android.javax.sip.header.CSeqHeader;
import android.javax.sip.header.CallIdHeader;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.Formatter;

import org.apache.http.conn.util.InetAddressUtils;
import org.apache.log4j.spi.ErrorCode;
import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.impl.AccountManagerImpl;
import org.mobicents.restcomm.android.sipua.impl.SecurityHelper;
import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class JainSipClient implements SipListener {
    public JainSipClientListener listener;
    JainSipMessageBuilder jainSipMessageBuilder;
    Context androidContext;
    HashMap<String, Object> configuration;
    boolean clientConnected = false;
    static final String TAG = "JainSipClient";
    // android handler token to identify registration refresh posts
    final int REGISTER_REFRESH_HANDLER_TOKEN = 1;
    Handler signalingHandler;
    final int DEFAULT_REGISTER_EXPIRY_PERIOD = 60;
    // the registration refresh needs to happen sooner than expiry to make sure that the client has a registration at all times. Let's
    // set it to EXPIRY - 10 seconds. TODO: in the future we could randomize this so that for example it is between half the expiry
    // and full expiry (in this example, a random between [30, 60] seconds) to avoid having all android clients refreshing all at
    // the same time and stressing Restcomm. Actually this is how Sofia SIP in restcomm-ios-sdk does it by default.
    final int REGISTER_REFRESH_MINUS_INTERVAL = 10;

    // JAIN SIP entities
    public SipFactory jainSipFactory;
    public SipStack jainSipStack;
    public ListeningPoint jainSipListeningPoint;
    public SipProvider jainSipProvider;

    JainSipTransactionManager jainSipTransactionManager;

    JainSipClient(Handler signalingHandler) {
        this.signalingHandler = signalingHandler;
    }

    void open(String id, Context androidContext, HashMap<String, Object> parameters, JainSipClientListener listener, boolean connectivity, SipManager.NetworkInterfaceType networkInterfaceType) {
        this.listener = listener;
        this.androidContext = androidContext;
        configuration = parameters;
        jainSipMessageBuilder = new JainSipMessageBuilder();
        jainSipTransactionManager = new JainSipTransactionManager(this);

        jainSipFactory = SipFactory.getInstance();
        jainSipFactory.resetFactory();
        jainSipFactory.setPathName("android.gov.nist");

        Properties properties = new Properties();
        properties.setProperty("android.javax.sip.STACK_NAME", "androidSip");

        boolean secure = false;
        if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED) && parameters.get(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED) == true) {
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
            jainSipStack = jainSipFactory.createSipStack(properties);
            jainSipMessageBuilder.initialize(jainSipFactory);

            if (connectivity) {
                jainSipTransactionManager.add(id, JainSipTransaction.Type.TYPE_OPEN, parameters);
            }
        } catch (SipException e) {
            listener.onClientOpenedEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP));
            RCLogger.e(TAG, "open(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    void close(final String id) {
        RCLogger.v(TAG, "close()");

        if (clientConnected) {
            // cancel any pending scheduled registrations
            //signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);

            // TODO: close any active calls
            //

            jainSipTransactionManager.removeAll();

            if (configuration.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !configuration.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                // non registrar-less, we need to unregister and when done shutdown
                jainSipTransactionManager.add(id, JainSipTransaction.Type.TYPE_CLOSE, this.configuration);
            }
            else {
                // registrar-less, just shutdown and notify UI thread
                try {
                    unbind();
                    jainSipStopStack();

                    listener.onClientClosedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                } catch (JainSipException e) {
                    RCLogger.w(TAG, "close(): close failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                    listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                }
            }
        } else {
            RCLogger.w(TAG, "close(): JAIN SIP client already closed, bailing");
            listener.onClientClosedEvent(id, RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP));
        }
    }

    void reconfigure(String id, Context androidContext, HashMap<String, Object> parameters, JainSipClientListener listener, boolean connectivity, SipManager.NetworkInterfaceType networkInterfaceType)
    {
        // check which parameters actually changed by comparing this.configuration with parameters
        HashMap<String, Object> modifiedParameters = JainSipConfiguration.modifiedParameters(this.configuration, parameters);

        if (modifiedParameters.size() == 0) {
            listener.onClientReconfigureEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
            return;
        }

        HashMap<String, Object> oldParameters = new HashMap<String, Object>();
        oldParameters.putAll(configuration);

        // remember that the new parameters can be just a subset of the currently stored in configuration, so to update the current parameters we need
        // to merge them with the new (i.e. keep the old and replace any new keys with new values)
        configuration = JainSipConfiguration.mergeParameters(configuration, parameters);

        // Set the media parameters right away, since they are irrelevant to signaling
        if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED)) {
            this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_ENABLED));
        }
        if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_URL)) {
            this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_URL, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_URL));
        }
        if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_USERNAME)) {
            this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_USERNAME, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_USERNAME));
        }
        if (modifiedParameters.containsKey(RCDevice.ParameterKeys.MEDIA_TURN_PASSWORD)) {
            this.configuration.put(RCDevice.ParameterKeys.MEDIA_TURN_PASSWORD, modifiedParameters.get(RCDevice.ParameterKeys.MEDIA_TURN_PASSWORD));
        }

        if (modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_SECURE_ENABLED)) {
            // if signaling has changed we need to a. unregister from old using old creds, b. unbind, c. bind, d. register with new using new creds
            // start FSM and pass it both previous and current parameters
            HashMap<String, Object> multipleParameters = new HashMap<String, Object>();
            multipleParameters.put("old-parameters", oldParameters);
            multipleParameters.put("new-parameters", configuration);
            jainSipTransactionManager.add(id, JainSipTransaction.Type.TYPE_RECONFIGURE_RELOAD_NETWORKING, multipleParameters);
        }
        else if (modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_USERNAME) ||
                    modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_PASSWORD) ||
                            modifiedParameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN)) {
                // if username, password or domain has changed we need to a. unregister from old using old creds and b. register with new using new creds
                // start FSM and pass it both previous and current parameters
                jainSipTransactionManager.add(id, JainSipTransaction.Type.TYPE_RECONFIGURE, this.configuration);
        }
        else {
            listener.onClientReconfigureEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
        }
    }

    void call(String id, HashMap<String, Object> parameters) {
    }

    void sendMessage(String id, String peer, String text) {

    }

    // Setup JAIN networking facilities
    public void bind(String id, boolean secure, SipManager.NetworkInterfaceType networkInterfaceType) throws JainSipException {
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
            } catch (SocketException e) {
                RCLogger.i(TAG, "register(): " + e.getMessage());
                e.printStackTrace();
                throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_INTERFACE,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_INTERFACE));
            }
            catch (Exception e) {
                RCLogger.e(TAG, "register(): " + e.getMessage());
                e.printStackTrace();
                throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_BINDING,
                        RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_NETWORK_BINDING));

            }
        }
    }

    // Release JAIN networking facilities
    public void unbind() throws JainSipException {
        RCLogger.w(TAG, "unbind()");
        if (jainSipListeningPoint != null) {
            try {
                jainSipProvider.removeSipListener(this);
                if (jainSipProvider.getListeningPoints().length > 1) {
                    RCLogger.e(TAG, "unbind(): Listening Point count > 1: " + jainSipProvider.getListeningPoints().length);
                }
                jainSipStack.deleteSipProvider(jainSipProvider);
                jainSipStack.deleteListeningPoint(jainSipListeningPoint);

                jainSipListeningPoint = null;
            } catch (ObjectInUseException e) {
                RCLogger.e(TAG, "unbind(): " + e.getMessage());
                e.printStackTrace();
                throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
            }
        }
    }

    int jainSipStartStack(String id) {
        // TODO: Didn't have that in the past, hope it doesn't cause any issues
        try {
            jainSipStack.start();
            clientConnected = true;

        } catch (SipException e) {
            RCLogger.e(TAG, "jainSipStartStack(): " + e.getMessage());
            e.printStackTrace();
            return RCClient.ErrorCodes.ERROR_SIGNALING_SIP_STACK_BOOTSTRAP.ordinal();
        }
        return RCClient.ErrorCodes.SUCCESS.ordinal();
    }

    void jainSipStopStack() {
        jainSipStack.stop();
        jainSipMessageBuilder.shutdown();
        jainSipFactory.resetFactory();

        /*
        configuration = null;
        androidContext = null;
        listener = null;
        jainSipFsm = null;
        */

        clientConnected = false;
    }

    public ClientTransaction jainSipRegister(String id, final HashMap<String, Object> parameters, JainSipTransaction.RegistrationType registrationType) throws JainSipException {
        RCLogger.v(TAG, "jainSipRegister()");

        int expiry = DEFAULT_REGISTER_EXPIRY_PERIOD;
        if (parameters.containsKey("signaling-register-expiry") && !parameters.get("signaling-register-expiry").equals("")) {
            expiry = (Integer) parameters.get("signaling-register-expiry");
            if (expiry <= REGISTER_REFRESH_MINUS_INTERVAL) {
                RCLogger.w(TAG, "jainSipRegister(): Register expiry period too small, using default: " + DEFAULT_REGISTER_EXPIRY_PERIOD);
                expiry = DEFAULT_REGISTER_EXPIRY_PERIOD;
            }
        }

        ClientTransaction transaction = null;
        try {
            Request registerRequest = jainSipMessageBuilder.buildRegister(id, listener, jainSipListeningPoint, expiry, null, parameters);
            if (registerRequest != null) {
                RCLogger.v(TAG, "jainSipRegister(): Sending SIP request: \n" + registerRequest.toString());

                // Remember that this might block waiting for DNS server
                transaction = this.jainSipProvider.getNewClientTransaction(registerRequest);
                transaction.sendRequest();
            }
        } catch (SipException e) {
            RCLogger.e(TAG, "jainSipRegister(): " + e.getMessage());
            e.printStackTrace();
            throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT));
        }

        // cancel any pending scheduled registrations (in case this is an on-demand registration and we end up posting to handler on top of the old)
        signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);
        // schedule a registration update after 'registrationRefresh' seconds
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                jainSipTransactionManager.add(Long.toString(System.currentTimeMillis()), JainSipTransaction.Type.TYPE_REGISTER_REFRESH, parameters);
            }
        };
        signalingHandler.postAtTime(runnable, REGISTER_REFRESH_HANDLER_TOKEN, SystemClock.uptimeMillis() + (expiry - REGISTER_REFRESH_MINUS_INTERVAL) * 1000);

        return transaction;
    }

    public ClientTransaction jainSipUnregister(String id, final HashMap<String, Object> parameters, JainSipTransaction.RegistrationType registrationType) throws JainSipException {
        RCLogger.v(TAG, "jainSipUnregister()");
        ClientTransaction transaction = null;
        try {
            Request registerRequest = jainSipMessageBuilder.buildRegister(id, listener, jainSipListeningPoint, 0, null, parameters);
            if (registerRequest != null) {
                RCLogger.v(TAG, "jainSipUnregister(): Sending SIP request: \n" + registerRequest.toString());

                // Remember that this might block waiting for DNS server
                transaction = this.jainSipProvider.getNewClientTransaction(registerRequest);
                transaction.sendRequest();
            }
        } catch (SipException e) {
            RCLogger.e(TAG, "jainSipUnregister(): " + e.getMessage());
            e.printStackTrace();
            throw new JainSipException(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_COULD_NOT_CONNECT));
        }

        // cancel any pending scheduled registrations
        signalingHandler.removeCallbacksAndMessages(REGISTER_REFRESH_HANDLER_TOKEN);

        return transaction;
    }

    // -- SipListener events
    // Remember that SipListener events run in a separate thread created by JAIN SIP, which makes sharing of resources between our signaling thread and this
    // JAIN SIP thread a bit difficult. To avoid that let's do the actual handling of these events in the signaling thread.
    public void processRequest(RequestEvent requestEvent) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // TODO:
            }
        };
        signalingHandler.post(runnable);
    }

    public void processResponse(final ResponseEvent responseEvent) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                ResponseEventExt responseEventExt = (ResponseEventExt) responseEvent;
                Response response = responseEvent.getResponse();
                RCLogger.v(TAG, "Received SIP response: \n" + response.toString());

                CallIdHeader callIdHeader = (CallIdHeader) response.getHeader("Call-ID");
                String callId = callIdHeader.getCallId();
                JainSipTransaction jainSipTransaction = jainSipTransactionManager.getUsingTransactionId(callId);
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
                                    new AccountManagerImpl((String) jainSipTransaction.parameters.get(RCDevice.ParameterKeys.SIGNALING_USERNAME),
                                            responseEventExt.getRemoteIpAddress(), (String) jainSipTransaction.parameters.get(RCDevice.ParameterKeys.SIGNALING_PASSWORD)), jainSipMessageBuilder.jainSipHeaderFactory);

                            // we 're subtracting one since the first attempt has already taken place
                            // (that way we are enforcing MAX_AUTH_ATTEMPTS at most)
                            if (jainSipTransaction.shouldRetry()) {
                                ClientTransaction authenticationTransaction = authenticationHelper.handleChallenge(response, (ClientTransaction) jainSipTransaction.transaction, jainSipProvider, 5, true);

                                // update previous transaction with authenticationTransaction (remember that previous ended with 407 final response)
                                String authCallId = ((CallIdHeader)authenticationTransaction.getRequest().getHeader("Call-ID")).getCallId();
                                jainSipTransaction.updateTransaction(authenticationTransaction);
                                jainSipTransaction.updateTransactionId(authCallId);
                                RCLogger.v(TAG, "Sending SIP request: \n" + authenticationTransaction.getRequest().toString());
                                authenticationTransaction.sendRequest();
                                jainSipTransaction.increaseAuthAttempts();
                            } else {
                                jainSipTransaction.processFsm(jainSipTransaction.id, "register-failure", RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_MAX_RETRIES_REACHED,
                                        RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_MAX_RETRIES_REACHED));
                                //jainSipTransactionManager.remove(callId);
                            }
                        } catch (Exception e) {
                            jainSipTransaction.processFsm(jainSipTransaction.id, "register-failure", RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED,
                                    RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));

                            //listener.onClientOpenedEvent(callId, RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_UNHANDLED));
                            //jainSipTransactionManager.remove(callId);
                            RCLogger.e(TAG, "register(): " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else if (response.getStatusCode() == Response.FORBIDDEN) {
                        jainSipTransaction.processFsm(jainSipTransaction.id, "register-failure", RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_FORBIDDEN,
                                RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_AUTHENTICATION_FORBIDDEN));
                        //jainSipTransactionManager.remove(callId);
                    } else if (response.getStatusCode() == Response.SERVICE_UNAVAILABLE) {
                        jainSipTransaction.processFsm(callId, "register-failure", RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_SERVICE_UNAVAILABLE,
                                RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_SERVICE_UNAVAILABLE));
                    } else if (response.getStatusCode() == Response.OK) {
                        // register succeeded
                        jainSipTransaction.processFsm(jainSipTransaction.id, "register-success", RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                        //jainSipTransactionManager.remove(callId);
                    }
                } else if (method.equals(Request.INVITE)) {
                    // TODO: forward to JainSipCall for processing

                }
            }
        };
        signalingHandler.post(runnable);
    }

    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // TODO:
            }
        };
        signalingHandler.post(runnable);

        /*
                RCLogger.i(TAG, "SipManager.processDialogTerminated: " + dialogTerminatedEvent.toString() + "\n" +
                        "\tdialog: " + dialogTerminatedEvent.getDialog().toString());
        */
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // TODO:
            }
        };
        signalingHandler.post(runnable);

        /*
                RCLogger.e(TAG, "SipManager.processIOException: " + exceptionEvent.toString() + "\n" +
                        "\thost: " + exceptionEvent.getHost() + "\n" +
                        "\tport: " + exceptionEvent.getPort());
        */
    }

    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // TODO:
            }
        };
        signalingHandler.post(runnable);

        /*
                RCLogger.i(TAG, "SipManager.processTransactionTerminated: " + transactionTerminatedEvent.toString() + "\n" +
                        "\tclient transaction: " + transactionTerminatedEvent.getClientTransaction() + "\n" +
                        "\tserver transaction: " + transactionTerminatedEvent.getServerTransaction() + "\n" +
                        "\tisServerTransaction: " + transactionTerminatedEvent.isServerTransaction());
        */
    }

    public void processTimeout(final TimeoutEvent timeoutEvent) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Request request;
                if (timeoutEvent.isServerTransaction()) {
                    request = timeoutEvent.getServerTransaction().getRequest();
                } else {
                    request = timeoutEvent.getClientTransaction().getRequest();
                }

                RCLogger.i(TAG, "processTimeout(): method: " + request.getMethod() + " URI: " + request.getRequestURI());
                CallIdHeader callIdHeader = (CallIdHeader) request.getHeader("Call-ID");
                String callId = callIdHeader.getCallId();
                JainSipTransaction jainSipTransaction = jainSipTransactionManager.getUsingTransactionId(callId);
                if (jainSipTransaction == null) {
                    // transaction is not identified, just emit a log error; don't notify UI thread
                    RCLogger.i(TAG, "processTimeout(): transaction not identified");
                    return;
                }

                if (jainSipTransaction.type == JainSipTransaction.Type.TYPE_REGISTRATION) {
                    // TODO: need to handle registration refreshes
                    jainSipTransaction.processFsm(jainSipTransaction.id, "timeout", RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_TIMEOUT, RCClient.errorText(RCClient.ErrorCodes.ERROR_SIGNALING_REGISTER_TIMEOUT));
                    //jainSipTransactionManager.remove(callId);
                } else if (jainSipTransaction.type == JainSipTransaction.Type.TYPE_CALL) {
                    // TODO: call JainSipCall.processTimeout()
                } else if (jainSipTransaction.type == JainSipTransaction.Type.TYPE_MESSAGE) {
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
        };
        signalingHandler.post(runnable);
    }


    // -- Helpers

    // TODO: Improve this, try to not depend on such low level facilities
    public String getIPAddress(String id, boolean useIPv4, SipManager.NetworkInterfaceType networkInterfaceType) throws SocketException
    {
        RCLogger.i(TAG, "getIPAddress()");
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
        return "";
    }

}
