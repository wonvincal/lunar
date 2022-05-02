package com.lunar.strategy.statemachine;

import java.util.HashMap;
import java.util.Map;

import com.lunar.strategy.statemachine.State;

/*
 * Builds State Machine
 * Ease of use is important while performance is not important here 
 * @author swordsaint
 *
 */
public class StateMachineBuilder {
	final private String m_name;
	final private Map<Integer, State> m_states = new HashMap<Integer, State>();
	final private Map<State, Map<Integer, State>> m_stateTransitions = new HashMap<State, Map<Integer, State>>();
	final private Map<State, Map<Integer, EventTranslator>> m_eventTranslators = new HashMap<State, Map<Integer, EventTranslator>>();
	private int m_maxStateId = 0;
	private int m_maxTransitionId = 0;
	private int m_maxEventId = 0;
	
	public StateMachineBuilder(final String name) {
		m_name = name;
	}
	
	public boolean registerState(final State state) {
		if (state.getStateId() < 0 ||  m_states.containsKey(state.getStateId()))
			return false;
		m_states.put(state.getStateId(), state);
		m_maxStateId = Math.max(m_maxStateId, state.getStateId());
		return true;						
	}
	
	public boolean linkStates(final int stateId, final int transitionId, final int nextStateId) {
		if (stateId < 0 || transitionId < 0 || nextStateId < 0)
			return false;
		final State state = m_states.get(stateId);
		if (state == null)
			return false;
		final State nextState = m_states.get(nextStateId);
		if (nextState == null)
			return false;
		m_stateTransitions.putIfAbsent(state, new HashMap<Integer, State>());
		final Map<Integer, State> stateTransitions = m_stateTransitions.get(state);			
		if (stateTransitions.containsKey(transitionId))
			return false;
		stateTransitions.put(transitionId, nextState);
		m_maxTransitionId = Math.max(m_maxTransitionId, transitionId);
		return true;
	}
	
	public boolean translateEvent(final int[] stateIds, final int eventId, final EventTranslator eventTranslator) {
	    boolean result = true;
	    for (final int stateId : stateIds) {
	        result &= translateEvent(stateId, eventId, eventTranslator);
	    }
	    return result;
	}
	
	public boolean translateEvent(final int stateId, final int eventId, final EventTranslator eventTranslator) {
		if (stateId < 0 || eventId < 0)
			return false;
		final State state = m_states.get(stateId);
		if (state == null)
			return false;
		m_eventTranslators.putIfAbsent(state, new HashMap<Integer, EventTranslator>());
		final Map<Integer, EventTranslator> translator = m_eventTranslators.get(state);
		if (translator.containsKey(eventId))
			return false;
		translator.put(eventId, eventTranslator);
		m_maxEventId = Math.max(m_maxEventId, eventId);
		return true;
	}
	
	public StateMachine buildMachine() {
		final State[] states = new State[m_maxStateId + 1];
		final State[][] transitions = new State[m_maxStateId + 1][m_maxTransitionId + 1];
		final EventTranslator[][] translators = new EventTranslator[m_maxStateId + 1][m_maxEventId + 1];
		for (int i = 0; i < m_maxStateId + 1; i++) {
			final State state = m_states.get(i);
			final Map<Integer, State> localTransitions = m_stateTransitions.get(state);
			final Map<Integer, EventTranslator> localTranslators = m_eventTranslators.get(state);
			states[i] = state;			
			for (int j = 0; j < m_maxTransitionId + 1; j++) {
				if (localTransitions == null) {
					transitions[i][j] = null;
				}
				else {
					final State nextState  = localTransitions.get(j);
					transitions[i][j] = nextState;
				}
			}
			for (int j = 0; j < m_maxEventId + 1; j++) {
				if (localTranslators == null) {
					translators[i][j] = null;
				}
				else {
					final EventTranslator translator  = localTranslators.get(j);
					translators[i][j] = translator;
				}
			}
		}
		return new StateMachine(m_name, states, transitions, translators);
	}
}