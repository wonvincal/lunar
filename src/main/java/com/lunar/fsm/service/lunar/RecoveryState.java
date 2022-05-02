package com.lunar.fsm.service.lunar;

import static com.lunar.fsm.service.lunar.Transitions.NO_TRANSITION;
import static com.lunar.fsm.service.lunar.Transitions.in_ANY_STATE_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_RECOVERY_receive_ACTIVATE;
import static com.lunar.fsm.service.lunar.Transitions.in_RECOVERY_receive_FAIL;
import static com.lunar.fsm.service.lunar.Transitions.in_RECOVERY_receive_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_RECOVERY_receive_THREAD_STOP;
import static com.lunar.fsm.service.lunar.Transitions.in_RECOVERY_receive_WAIT;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RecoveryState extends State{
    static final Logger LOG = LogManager.getLogger(RecoveryState.class);

    @Override
    Transition enter(LunarService service, StateTransitionEvent event) {
        if (event == StateTransitionEvent.RECOVER){
            return onEvent(service, service.serviceRecoveryEnter());
        } 
        LOG.error("received unexpected event {} on enter", event);
        return NO_TRANSITION;
    }
    
    @Override
    State exit(LunarService service, StateTransitionEvent event) {
        service.serviceRecoveryExit();
        return this;
    }

    @Override
    Transition onEvent(LunarService service, StateTransitionEvent event) {
        if (event == StateTransitionEvent.NULL){
            return NO_TRANSITION;
        }
        else if (event == StateTransitionEvent.ACTIVATE){
            return in_RECOVERY_receive_ACTIVATE;
        }
        else if (event == StateTransitionEvent.FAIL){
            return in_RECOVERY_receive_FAIL;
        }
        else if (event == StateTransitionEvent.WAIT){
            return in_RECOVERY_receive_WAIT;
        }
        else if (event == StateTransitionEvent.THREAD_STOP){
            return in_RECOVERY_receive_THREAD_STOP;
        }
        else if (event == StateTransitionEvent.STOP){
            return in_RECOVERY_receive_STOP;
        }
        LOG.error("Unexpected event {}, treat this as FAIL", event);
        return in_ANY_STATE_receive_FAIL;
    }
}
