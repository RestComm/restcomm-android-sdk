package org.mobicents.restcomm.android.client.sdk;

import android.javax.sip.Transaction;

import java.util.HashMap;

public class JainSipTransaction {
    public enum Type {
        TYPE_REGISTRATION,
        TYPE_CALL,
        TYPE_MESSAGE,
    }

    public enum RegistrationType {
        REGISTRATION_INITIAL,
        REGISTRATION_REFRESH,
        REGISTRATION_UNREGISTER,
    }

    public String id;
    public Type type;
    public RegistrationType registrationType = RegistrationType.REGISTRATION_INITIAL;
    public Transaction transaction;
    public HashMap<String, Object> parameters;
    public Runnable onCompletion;
    public int authenticationAttempts;
    public static int MAX_AUTH_ATTEMPTS = 3;

    JainSipTransaction(String id, Type type, RegistrationType registrationType, Transaction transaction, HashMap<String, Object> parameters, Runnable onCompletion)
    {
        this.id = id;
        this.type = type;
        this.registrationType = registrationType;
        this.transaction = transaction;
        this.parameters = parameters;
        this.authenticationAttempts = 0;
        this.onCompletion = onCompletion;
    }

    JainSipTransaction(String id, Type type, Transaction transaction, HashMap<String, Object> parameters)
    {
        this(id, type, RegistrationType.REGISTRATION_INITIAL, transaction, parameters, null);
    }

    JainSipTransaction(String id, Type type, RegistrationType registrationType, Transaction transaction, HashMap<String, Object> parameters)
    {
        this(id, type, registrationType, transaction, parameters, null);
    }

    void update(Transaction transaction)
    {
        this.transaction = transaction;
    }

    // Should we retry authentication if previous failed? We retry a max of MAX_AUTH_ATTEMPTS
    boolean shouldRetry()
    {
        if (authenticationAttempts < JainSipTransaction.MAX_AUTH_ATTEMPTS - 1) {
            return true;
        }
        else {
            return false;
        }
    }

    void increaseAuthAttempts()
    {
        authenticationAttempts += 1;
    }
}
