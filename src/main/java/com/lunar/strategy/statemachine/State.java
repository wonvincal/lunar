package com.lunar.strategy.statemachine;

abstract public class State {
	final private int m_id;
	
	public State(final int id) {
		m_id = id;
	}
	
	public int getStateId() {
		return m_id;
	}

	/*
	 * This gets called when the state is entered from another state
	 */
	abstract public void beginState(final int prevState, final int transitionId) throws Exception;
	
}
