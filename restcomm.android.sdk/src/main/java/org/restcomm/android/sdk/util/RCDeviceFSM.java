/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * For questions related to commercial use licensing, please contact sales@telestax.com.
 *
 */

package org.restcomm.android.sdk.util;

import org.restcomm.android.sdk.RCClient;
import org.restcomm.android.sdk.RCDevice;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

// TODO:
// - Add more doc
// - Try to move back to RCDevice as inner class (non static) as it will simplify access to RCDevice resources A LOT)
// - See if we can avoid calling terminate() inside the handler methods
// - See if we can improve the savedContext
// - Make sure that once the FSM is done its state is reset to initialState so that the same FSM instance can be reused (for example for a RCDevice.reconfigure())
// - Remote static from RCDevice state
// - Fix exception 'java.lang.NoClassDefFoundError: Failed resolution of: Ljava/beans/Introspector' on FSM builder creation
// Define FSM class to synchronize parallel registration of signaling and push notifications facilities and present a single success/failure point

//@StateMachineParameters(stateType=RCDeviceFSM.FSMState.class, eventType=RCDeviceFSM.FSMEvent.class, contextType=RCDeviceFSM.FsmContext.class)
//public class RCDeviceFSM extends AbstractUntypedStateMachine {
public class RCDeviceFSM extends AbstractStateMachine<RCDeviceFSM, RCDeviceFSM.FSMState, RCDeviceFSM.FSMEvent, FsmContext> {
    private static final String TAG = "RCDeviceFSM";

    // Define FSM events
    public enum FSMEvent {
        signalingRegistrationEvent,  // signaling facilities registration finished
        //signalingReconfigurationEvent,  // signaling reconfiguration finished
        pushRegistrationEvent,  // push registered finished
        pushRegistrationNotNeededEvent,  // push registration isn't needed (it's already up to date)
        resetStateMachine,  // reset state machine to original state
    }

    // Define FSM states
    public enum FSMState {
        initialState,
        signalingReadyState,
        pushReadyState,
        finishedState,
    }

    RCDeviceFSM(RCDevice device)
    {
        this.device = device;
    }

    // keep internal context as we need to keep data around between state changes
    FsmContext savedContext;
    // keep RCDevice reference so that we can use the listeners when needed
    RCDevice device;

    protected void toPushReady(FSMState from, FSMState to, FSMEvent event, FsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.signalingReadyState) {
            // we are already registered signaling-wise, which means we a can notify app and then we 're done
            if (device.isAttached()) {
                if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                    device.getDeviceListener().onInitialized(device, context.connectivityStatus, context.status.ordinal(), RCClient.errorText(context.status));
                } else {
                    // we have an error/warning previously stored, so we need to convey it to the user
                    device.getDeviceListener().onInitialized(device, savedContext.connectivityStatus, savedContext.status.ordinal(), savedContext.text);
                }
            } else {
                if (context.status == RCClient.ErrorCodes.SUCCESS) {
                    RCLogger.i(TAG, "Device and user are successfully registered for Push Notifications!");
                } else {
                    RCLogger.w(TAG, "RegisterForPush  warning: " + RCClient.errorText(context.status));
                }
            }

            // terminate the FSM as this was the last state; didn't see any examples on this, so better double check if it's correct
            //terminate();
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    protected void toSignalingInitializationReady(FSMState from, FSMState to, FSMEvent event, FsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.pushReadyState) {
            // we are already registered push-wise, which means we a can notify app and then we 're done
            if (device.isAttached()) {
                if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                    device.getDeviceListener().onInitialized(device, context.connectivityStatus, context.status.ordinal(), context.text);
                } else {
                    // we have an error/warning previously stored, so we need to convey it to the user
                    device.getDeviceListener().onInitialized(device, savedContext.connectivityStatus, savedContext.status.ordinal(), savedContext.text);
                }
            } else {
                RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onInitialized(): " +
                        RCClient.errorText(context.status));
            }

            // the device state is not affected by savedContext, that is even if push registration is messed up, the device is still ready
            if (context.status == RCClient.ErrorCodes.SUCCESS) {
                device.state = RCDevice.DeviceState.READY;
            } else {
                device.release();
            }

            // terminate the FSM as this was the last state; didn't see any examples on this, so better double check if it's correct
            //terminate();
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    protected void toPushReconfigurationReady(FSMState from, FSMState to, FSMEvent event, FsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.signalingReadyState) {
            // we are already registered signaling-wise, which means we a can notify app and then we 're done
            if (device.isAttached()) {
                if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                    device.getDeviceListener().onReconfigured(device, context.connectivityStatus, context.status.ordinal(), RCClient.errorText(context.status));
                } else {
                    // we have an error/warning previously stored, so we need to convey it to the user
                    device.getDeviceListener().onReconfigured(device, savedContext.connectivityStatus, savedContext.status.ordinal(), savedContext.text);
                }
            } else {
                if (context.status == RCClient.ErrorCodes.SUCCESS) {
                    RCLogger.i(TAG, "Device and user are successfully registered for Push Notifications!");
                } else {
                    RCLogger.w(TAG, "RegisterForPush  warning: " + RCClient.errorText(context.status));
                }
            }

            // terminate the FSM as this was the last state; didn't see any examples on this, so better double check if it's correct
            // start over
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    protected void toSignalingReconfigurationReady(FSMState from, FSMState to, FSMEvent event, FsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");

        if (context.status == RCClient.ErrorCodes.SUCCESS) {
            device.state = RCDevice.DeviceState.READY;
        } else {
            device.state = RCDevice.DeviceState.OFFLINE;
        }

        if (from == FSMState.initialState) {
            // we haven't finished signaling registration; let's keep the state around so that we can use it when signaling is finished
            savedContext = context;
        } else if (from == FSMState.pushReadyState) {
            // we are already registered push-wise, which means we a can notify app and then we 're done
            if (device.isAttached()) {
                if (savedContext.status == RCClient.ErrorCodes.SUCCESS) {
                    device.getDeviceListener().onReconfigured(device, context.connectivityStatus, context.status.ordinal(), context.text);
                } else {
                    // we have an error/warning previously stored, so we need to convey it to the user
                    device.getDeviceListener().onReconfigured(device, savedContext.connectivityStatus, savedContext.status.ordinal(), savedContext.text);
                }
            } else {
                RCLogger.w(TAG, "RCDeviceListener event suppressed since Restcomm Client Service not attached: onReconfigured(): " +
                        RCClient.errorText(context.status));
            }

            // terminate the FSM as this was the last state; didn't see any examples on this, so better double check if it's correct
            //terminate();
            fire(FSMEvent.resetStateMachine);
        } else {
            // should never happen; let's add a Runtime Exception for as long we 're testing this to fix any isseues
            throw new RuntimeException("RCDevice FSM invalid state: " + to);
        }
    }

    protected void toInitialState(FSMState from, FSMState to, FSMEvent event, FsmContext context)
    {
        RCLogger.i(TAG, event + ": " + from + " -> " + to + ", context '" + context + "'");
        savedContext = null;
    }
}