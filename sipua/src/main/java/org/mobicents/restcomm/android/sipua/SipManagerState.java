package org.mobicents.restcomm.android.sipua;

	public enum SipManagerState {
        /**
         * This state is used while the SipManager tries to register the SIP account with the provider.
         */
        REGISTERING,
        /**
         * This state is used while the SipManager tries to unregister the SIP account from the provider.
         */
        UNREGISTERING, 
        /**
         * This state is used after the SipManager has successfully registered the SIP account with the provider.
         */
        READY,
        /**
         * This state is used when the SipManager the called contact's UA is signalling an incoming call to the user.
         */
        RINGING,
        /**
         * This state is used while the call is being established.
         */
        CALLING,
        /**
         * This state is used when the call is established.
         */
        ESTABLISHED,

        /**
         * This state is used when the SipManager is initialized.
         */
        IDLE,
        /**
         * This state is used when a call is incoming.
         */
        INCOMING,
        /**
         * This state is used when a signalling timeout occurred.
         */
        TIMEOUT,
        /**
         * This state is used when an error occurred.
         */
        ERROR,
        /**
         * This state is used when a contact is busy.
         */
        BUSY,
        /**
         * This state is used when the remote contact declined the call.
         */
        DECLINED,
        /**
         * This state is used when a remote contact is invalid.
         */
        INVALID,
        /**
         * This state is used when a remote contact ended the call.
         */
        BYE
}

