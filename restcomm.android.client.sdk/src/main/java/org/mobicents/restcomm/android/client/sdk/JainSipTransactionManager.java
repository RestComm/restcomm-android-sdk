package org.mobicents.restcomm.android.client.sdk;

import android.javax.sip.Transaction;

import java.util.HashMap;
import java.util.Map;

// Handles live JAIN SIP transactions. Each transaction is stored in 'transactions' map identified by an 'id' provided by the caller (in our RCDevice or RCConnection)
// and keeps information such as the JAIN SIP Transaction (Client or Server) amongst other things
public class JainSipTransactionManager {
    // TODO: consider using interface instead
    JainSipClient jainSipClient;
    HashMap<String, JainSipTransaction> transactions;


    JainSipTransactionManager(JainSipClient jainSipClient) {
        this.jainSipClient = jainSipClient;
        transactions = new HashMap<>();
    }

    void add(String id, JainSipTransaction.Type type, JainSipTransaction.RegistrationType registrationType, Transaction transaction, HashMap<String, Object> parameters) {
        //JainSipTransaction jainSipTransaction = new JainSipTransaction(this, jainSipClient, id, type, registrationType, transaction, parameters);
        JainSipTransaction jainSipTransaction = new JainSipTransaction(this, jainSipClient, id, type, registrationType, transaction, parameters);
        transactions.put(id, jainSipTransaction);
        jainSipTransaction.processFsm(id, "", null, null);
    }

    void add(String id, JainSipTransaction.Type type, Transaction transaction, HashMap<String, Object> parameters) {
        add(id, type, null, transaction, parameters);
    }

    void add(String id, JainSipTransaction.Type type, HashMap<String, Object> parameters) {
        add(id, type, null, null, parameters);
    }

    /*
    void add(String id, JainSipTransaction.Type type, JainSipTransaction.RegistrationType registrationType, Transaction transaction, HashMap<String, Object> parameters, Runnable onCompletion) {
        //synchronized (this) {
        JainSipTransaction t = new JainSipTransaction(jainSipClient, id, type, registrationType, transaction, parameters, onCompletion);
        //HashMap<String, Object> data = new HashMap<>();
        //data.put("transaction", transaction);
        //data.put("parameters", parameters);
        //data.put("type",);

        transactions.put(id, t);
        //}
    }
    */

    JainSipTransaction get(String id) {
        //synchronized (this) {
            if (transactions.containsKey(id)) {
                // this doesn't seem harmful let's suppress it for now
                //@SuppressWarnings("unchecked")
                return transactions.get(id);
            } else {
                return null;
            }
        //}
    }

    JainSipTransaction getUsingTransactionId(String transactionId)
    {
        for (Map.Entry<String, JainSipTransaction> entry : transactions.entrySet()) {
            JainSipTransaction transaction = entry.getValue();
            if (transaction.transactionId.equals(transactionId)) {
                return transaction;
            }
        }
        return null;
    }

    void remove(String id) {
        //synchronized (this) {
            if (transactions.containsKey(id)) {
                // this doesn't seem harmful let's suppress it for now
                //@SuppressWarnings("unchecked")
                transactions.remove(id);
            }
        //}
    }

    void removeAll() {
        //synchronized (this) {
            transactions.clear();
        //}
    }

}
