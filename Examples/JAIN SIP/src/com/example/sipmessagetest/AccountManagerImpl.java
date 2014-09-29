package com.example.sipmessagetest;

import gov.nist.android.javaxx.sip.clientauthutils.AccountManager;
import gov.nist.android.javaxx.sip.clientauthutils.UserCredentials;

import javaxx.sip.ClientTransaction;


public class AccountManagerImpl implements AccountManager {
    

    public UserCredentials getCredentials(ClientTransaction challengedTransaction, String realm) {
       return new UserCredentialsImpl(SipStackAndroid.getInstance().sipUserName,SipStackAndroid.getInstance().remoteIp,SipStackAndroid.getInstance().sipPassword);
    }

}
