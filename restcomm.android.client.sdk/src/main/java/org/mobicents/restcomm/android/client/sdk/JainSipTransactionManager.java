package org.mobicents.restcomm.android.client.sdk;

import android.javax.sip.Transaction;

import java.util.HashMap;

// Handles live JAIN SIP transactions. Each transaction is stored in 'transactions' map identified by an 'id' provided by the caller (in our RCDevice or RCConnection)
// and keeps information such as the JAIN SIP Transaction (Client or Server) amongst other things
public class JainSipTransactionManager {
    HashMap<String, JainSipTransaction> transactions;


    JainSipTransactionManager()
    {
        transactions = new HashMap<>();
    }

    void add(String id, JainSipTransaction.Type type, Transaction transaction, HashMap<String, Object> parameters)
    {
        synchronized (this) {
            JainSipTransaction t = new JainSipTransaction(id, type, transaction, parameters);
            //HashMap<String, Object> data = new HashMap<>();
            //data.put("transaction", transaction);
            //data.put("parameters", parameters);
            //data.put("type",);

            transactions.put(id, t);
        }
    }

    JainSipTransaction get(String id)
    {
        synchronized (this) {
            if (transactions.containsKey(id)) {
                // this doesn't seem harmful let's suppress it for now
                //@SuppressWarnings("unchecked")
                return transactions.get(id);
            } else {
                return null;
            }
        }
    }

    void remove(String id)
    {
        synchronized (this) {
            if (transactions.containsKey(id)) {
                // this doesn't seem harmful let's suppress it for now
                //@SuppressWarnings("unchecked")
                transactions.remove(id);
            }
        }
    }
}
