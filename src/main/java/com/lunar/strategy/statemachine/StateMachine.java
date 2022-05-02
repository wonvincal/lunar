package com.lunar.strategy.statemachine;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class StateMachine {
    static final Logger LOG = LogManager.getLogger(StateMachine.class);
    
	final private String m_name;
	final private State[] m_states;
	final private State[][] m_stateTransitions;
	final private EventTranslator[][] m_eventTranslators;
	private State m_currentState;
	private State[] m_currentTransitions;
	private EventTranslator[] m_currentTranslators;
	
	public StateMachine(final String name, final State[] states, final State[][] stateTransitions, final EventTranslator[][] eventTranslators) {
		m_name = name;
		m_states = states;
		m_stateTransitions = stateTransitions;
		m_eventTranslators = eventTranslators;
	}
	
	public String getName() {
		return m_name;
	}
	
	public int getCurrentStateId() {
		return m_currentState.getStateId();
	}
	
	public void start(final int stateId) throws Exception {
		//LOG.info("[{}] Starting statemachine at state {}", m_name, stateId);
		m_currentState = m_states[stateId];
		m_currentTransitions = m_stateTransitions[m_currentState.getStateId()];
		m_currentTranslators = m_eventTranslators[m_currentState.getStateId()];
		m_currentState.beginState(-1, -1);
	}
	
	public void onEventReceived(final int eventId) throws Exception {
		if (m_currentState != null) {
			final EventTranslator translator = m_currentTranslators[eventId];
			if (translator != null) {
			    final int transitionId = translator.translateToTransitionId(eventId);
	            if (transitionId >= 0) {
	                final State nextState = m_currentTransitions[transitionId];
	                if (nextState != null) {
	                    switchState(nextState, transitionId);
	                }
	            }
			}
		}
	}
	
	private void switchState(final State nextState, final int transitionId) throws Exception {
		//LOG.info("[{}] Switching state: {}:{} => {}", m_name, m_currentState.getStateId(), transitionId, nextState.getStateId());
	    final int prevState = m_currentState.getStateId();
	    m_currentState = nextState;
		m_currentTransitions = m_stateTransitions[m_currentState.getStateId()];
		m_currentTranslators = m_eventTranslators[m_currentState.getStateId()];
		nextState.beginState(prevState, transitionId);      
	}

}
