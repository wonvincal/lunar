package com.lunar.strategy.statemachine;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/*
 * Single thread only
 */
public interface StateMachineEventBus {
    boolean subscribeStateMachine(final long id, final StateMachine stateMachine);
    boolean unsubscribeStateMachine(final long id);
    void fireEvent(final int eventId) throws Exception;
    
    public class DynamicStateMachineEventBus implements StateMachineEventBus {
    	final Long2ObjectLinkedOpenHashMap<StateMachine> m_stateMachines;
    	
    	public DynamicStateMachineEventBus() {
    		m_stateMachines = new Long2ObjectLinkedOpenHashMap<StateMachine>();
    	}
    	
    	public boolean subscribeStateMachine(final long id, final StateMachine stateMachine) {
    		if (m_stateMachines.containsKey(id))
    			return false;
    		m_stateMachines.put(id, stateMachine);
    		return true;
    	}
    	
    	public boolean unsubscribeStateMachine(final long id) {
    		if (m_stateMachines.containsKey(id)) {
    			m_stateMachines.remove(id);
    			return true;
    		}
    		return false;
    	}
    	
    	public void fireEvent(final int eventId) throws Exception {
    		for (final StateMachine stateMachine : m_stateMachines.values()) {
    			stateMachine.onEventReceived(eventId);
    		}
    	}
    }
    
    public class StaticStateMachineEventBus implements StateMachineEventBus {
        final ObjectArrayList<StateMachine> m_stateMachines;
        
        public StaticStateMachineEventBus() {
            StateMachine[] stateMachines = new StateMachine[ObjectArrayList.DEFAULT_INITIAL_CAPACITY];
            m_stateMachines = ObjectArrayList.wrap(stateMachines);
            m_stateMachines.size(0);
        }
        
        @Override
        public boolean subscribeStateMachine(long id, StateMachine stateMachine) {
            if (m_stateMachines.contains(stateMachine))
                return false;
            m_stateMachines.add(stateMachine);
            return true;
        }

        @Override
        public boolean unsubscribeStateMachine(long id) {
            return false;
        }

        @Override
        public void fireEvent(int eventId) throws Exception {
            for (final StateMachine stateMachine : m_stateMachines) {
                stateMachine.onEventReceived(eventId);
            }
        }        
    }
    
    public class SingleStateMachineEventBus implements StateMachineEventBus {
        StateMachine m_stateMachine;
        
        public SingleStateMachineEventBus() {
            
        }
        
        @Override
        public boolean subscribeStateMachine(long id, StateMachine stateMachine) {
            if (m_stateMachine == null) {
                m_stateMachine = stateMachine;
                return true;
            }
            return false;
        }

        @Override
        public boolean unsubscribeStateMachine(long id) {
            return false;
        }

        @Override
        public void fireEvent(int eventId) throws Exception {
            m_stateMachine.onEventReceived(eventId);
        }        
    }    
}
