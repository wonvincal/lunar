package com.lunar.strategy;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class CircuitSwitch {
    private static final int MAX_PARENT_CIRCUITS = 3;

    private final ObjectArrayList<CircuitSwitch> m_parentCircuits;
    private final ObjectArrayList<CircuitSwitch> m_childCircuits;
    private boolean m_isActive;
    private boolean m_isSwitchedOn;

    public CircuitSwitch(final int initialChildCircuits) {
        m_parentCircuits = ObjectArrayList.wrap(new CircuitSwitch[MAX_PARENT_CIRCUITS]);
        m_parentCircuits.size(0);
        if (initialChildCircuits == 0) {
            m_childCircuits = null;
        }
        else {
            m_childCircuits = new ObjectArrayList<CircuitSwitch>(initialChildCircuits);
            m_childCircuits.size(0);
        }
        m_isActive = true;
        m_isSwitchedOn = false;
    }

    public void hookChildSwitch(final CircuitSwitch strategySwitch) {
        m_childCircuits.add(strategySwitch);
        strategySwitch.m_parentCircuits.add(this);
        strategySwitch.m_isActive = isOnAndActive() && strategySwitch.m_isActive;  
    }

    public boolean isOnAndActive() {
        return m_isSwitchedOn && m_isActive;
    }
    
    public boolean isOn() {
    	return m_isSwitchedOn;
    }

    public boolean switchOn() {
        if (canStartTask()) {
            m_isSwitchedOn = true;
            if (m_isActive) {
                if (m_childCircuits != null) {
                    for (final CircuitSwitch childSwitch : m_childCircuits) {
                        childSwitch.onParentSwitchedOn();
                    }
                }
                performTask();                
            }
        }
        return m_isSwitchedOn;
    }

    public boolean switchOff() {
        if (canStopTask()) {
            m_isSwitchedOn = false;
            if (m_childCircuits != null) {
                for (final CircuitSwitch childSwitch : m_childCircuits) {
                    childSwitch.onParentSwitchedOff();
                }
            }                
            stopTask();
        }
        return !m_isSwitchedOn;
    }

    private void onParentSwitchedOn() {
        if (m_parentCircuits != null) {
            for (final CircuitSwitch parentSwitch : m_parentCircuits) {            
                if (!parentSwitch.isOnAndActive()) {
                    return;
                }
            }
        }
        m_isActive = true;
        if (m_isSwitchedOn) {
            if (m_childCircuits != null) {
                for (final CircuitSwitch childSwitch : m_childCircuits) {
                    childSwitch.onParentSwitchedOn();
                }
            }
            performTask();
        }
    }

    private void onParentSwitchedOff() {
        m_isActive = false;
        if (m_childCircuits != null) {
            for (final CircuitSwitch childSwitch : m_childCircuits) {
                childSwitch.onParentSwitchedOff();
            }
        }
        stopTask();
    }

    protected boolean canStartTask() {
        return true;
    }
    
    protected void performTask() {

    }
    
    protected boolean canStopTask() {
        return true;
    }

    protected void stopTask() {

    }    
}
