package com.lunar.strategy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Security;
import com.lunar.marketdata.SpreadTable;
import com.lunar.strategy.statemachine.ConcreteState;
import com.lunar.strategy.statemachine.EventTranslator;
import com.lunar.strategy.statemachine.StateMachine;
import com.lunar.strategy.statemachine.StateMachineBuilder;

import static org.apache.logging.log4j.util.Unbox.box;

public class IssuerResponseTimeGenerator implements StrategySignalGenerator {
    static final Logger LOG = LogManager.getLogger(IssuerResponseTimeGenerator.class);    
    static final private long INITIAL_RESPONSE_LAG = 10_000_000_000L;
    static final private long FULL_RESPONSE_LAG = 3600_000_000_000L;
    
    public class StateIds {
        static public final int ERROR = 0;
        static public final int TIGHT_SPREAD = 1;
        static public final int WAITING_ASK_UP = 2;
        static public final int WAITING_TIGHT_SPREAD = 3;
    }

    public class EventIds {
        static public final int MM_ORDER_BOOK_UPDATED = 0;
        static public final int TIME_OUT = 1;
        static public final int TRIGGER_UP = 2;
    }    

    private class TransitionIds {
        static public final int ERROR = 0;
        static public final int TO_TIGHT = 1;
        static public final int WAIT_FOR_RESPONSE = 2;
    }
    
    private final StrategySecurity m_security;
    private final StateMachine m_stateMachine;
    private final StrategyScheduler m_scheduler;
    
    private IssuerResponseTimeHandler m_handler;
    
    private long m_nanoOfDay;
    private int m_mmBidLevel;
    private int m_mmAskLevel;
    private int m_targetSpread;
    private boolean m_isTight;
    private int m_prevMmAskLevel;    
    
    private long m_triggerNanoOfDay;
    private int m_triggerTargetSpread;
    
    private long m_initialResponseNanoOfDay;
    private int m_scheduleId = -1;
    private long m_scheduledNanoOfDay = -1;
    
    private int m_numberOfTriggers;
    private int m_numberOfResponses;
    private long m_totalTimeWaitingForResponse;
    private int m_numberOfFullResponses;
    private long m_totalTimeInWideSpread;
    
    final StrategyScheduler.Task m_timeoutTask;
    
    public IssuerResponseTimeGenerator(final StrategySecurity security, final StrategyScheduler scheduler) {
        m_security = security;
        m_scheduler = scheduler;
        m_timeoutTask = new StrategyScheduler.Task() {
            @Override
            public void run(final int scheduleId, final long scheduledNanoOfDay, final long actualNanoOfDay) {
                LOG.debug("Received timeout while waiting for issuer response: secCode {}, scheduleId {}, scheduledTimeOut {}, actualTimeOut {}", m_security.code(), box(scheduleId), box(scheduledNanoOfDay), box(actualNanoOfDay));
                try {
                    onReceiveTimeout(scheduleId, scheduledNanoOfDay);
                }
                catch (final Exception e) {
                    LOG.error("Unexpected exception caught...", e);
                }
            }
        }; 
        m_stateMachine = createStateMachineBuilder().buildMachine();
        try {
            m_stateMachine.start(StateIds.ERROR);
        }
        catch (final Exception e) {
            LOG.error("Unexpected exception occurred...",  e);
        }
    }
    
    public Security getSecurity() {
        return m_security;
    }

    private StateMachineBuilder createStateMachineBuilder() {
        final StateMachineBuilder builder = new StateMachineBuilder("IssuerResponseLag." + m_security.code());
        builder.registerState(new ConcreteState(StateIds.ERROR));
        builder.registerState(new ConcreteState(StateIds.TIGHT_SPREAD));
        builder.registerState(new ConcreteState(StateIds.WAITING_ASK_UP));
        builder.registerState(new ConcreteState(StateIds.WAITING_TIGHT_SPREAD));

        builder.linkStates(StateIds.ERROR, TransitionIds.TO_TIGHT, StateIds.TIGHT_SPREAD);
        builder.linkStates(StateIds.ERROR, TransitionIds.WAIT_FOR_RESPONSE, StateIds.WAITING_ASK_UP);

        builder.linkStates(StateIds.TIGHT_SPREAD, TransitionIds.WAIT_FOR_RESPONSE, StateIds.WAITING_ASK_UP);

        builder.linkStates(StateIds.WAITING_ASK_UP, TransitionIds.TO_TIGHT, StateIds.TIGHT_SPREAD);
        builder.linkStates(StateIds.WAITING_ASK_UP, TransitionIds.WAIT_FOR_RESPONSE, StateIds.WAITING_TIGHT_SPREAD);
        builder.linkStates(StateIds.WAITING_ASK_UP, TransitionIds.ERROR, StateIds.ERROR);

        builder.linkStates(StateIds.WAITING_TIGHT_SPREAD, TransitionIds.TO_TIGHT, StateIds.TIGHT_SPREAD);
        builder.linkStates(StateIds.WAITING_ASK_UP, TransitionIds.ERROR, StateIds.ERROR);

        
        builder.translateEvent(new int[] {StateIds.ERROR, StateIds.TIGHT_SPREAD}, EventIds.MM_ORDER_BOOK_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                return getStateTransitionBySpread();
            }
        });
        
        builder.translateEvent(StateIds.ERROR, EventIds.TRIGGER_UP, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                m_triggerNanoOfDay = m_nanoOfDay;
                m_triggerTargetSpread = m_targetSpread;
                if (scheduleTimeout(StrategyScheduler.ScheduleIds.WAIT_ISSUER_INITIAL_RESPONSE, INITIAL_RESPONSE_LAG)) {
                    m_numberOfTriggers++;
                    return TransitionIds.WAIT_FOR_RESPONSE;
                }
                // if cannot schedule the timer, then just discard this trigger response
                return -1;
            }
        });
        builder.translateEvent(StateIds.TIGHT_SPREAD, EventIds.TRIGGER_UP, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                m_triggerNanoOfDay = m_nanoOfDay;
                m_triggerTargetSpread = m_targetSpread;
                if (scheduleTimeout(StrategyScheduler.ScheduleIds.WAIT_ISSUER_INITIAL_RESPONSE, INITIAL_RESPONSE_LAG)) {
                    m_numberOfTriggers++;
                    return TransitionIds.WAIT_FOR_RESPONSE;
                }
                // if cannot schedule the timer, then just discard this trigger response
                return -1;
            }
        });
        
        builder.translateEvent(StateIds.WAITING_ASK_UP, EventIds.MM_ORDER_BOOK_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_prevMmAskLevel >= SpreadTable.SPREAD_TABLE_MIN_LEVEL) {
                    if (m_mmAskLevel == 0 || m_mmAskLevel > m_prevMmAskLevel) {
                        m_initialResponseNanoOfDay = m_nanoOfDay;
                        final long issuerResponseTime = m_nanoOfDay - m_triggerNanoOfDay;
                        m_numberOfResponses++;
                        m_totalTimeWaitingForResponse += issuerResponseTime;
                        LOG.debug("Received initial issuer response: secCode {}, responseTime {}, trigger seqNum {}",  m_security.code(), box(issuerResponseTime), box(m_security.orderBook().triggerInfo().triggerSeqNum()));
                        updateIssuerLag(issuerResponseTime);
                        if (scheduleTimeout(StrategyScheduler.ScheduleIds.WAIT_ISSUER_FULL_RESPONSE, FULL_RESPONSE_LAG)) {
                            return TransitionIds.WAIT_FOR_RESPONSE;
                        }
                        // if cannot schedule the timer, then just don't bother finding the full response time
                        return getStateTransitionBySpread();
                    }
                }
                return -1;
            }
        });
        
        builder.translateEvent(StateIds.WAITING_ASK_UP, EventIds.TIME_OUT, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                LOG.debug("Timed out while waiting for ASK_UP signal: secCode {}", m_security.code());
                return TransitionIds.ERROR;
            }
        });
        
        builder.translateEvent(StateIds.WAITING_TIGHT_SPREAD, EventIds.MM_ORDER_BOOK_UPDATED, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                if (m_targetSpread == m_triggerTargetSpread) {
                    if (m_isTight) {
                        final long issuerResponseTime = m_nanoOfDay - m_triggerNanoOfDay;
                        final long timeInWideSpread = m_nanoOfDay - m_initialResponseNanoOfDay;
                        m_numberOfFullResponses++;
                        m_totalTimeInWideSpread += timeInWideSpread;
                        LOG.debug("Received full issuer response: secCode {}, responseTime {}, timeInWideSpread {}, trigger seqNum {}",  m_security.code(), box(issuerResponseTime), box(timeInWideSpread), box(m_security.orderBook().triggerInfo().triggerSeqNum()));
                        cancelTimeout();
                        updateIssuerSmoothing(timeInWideSpread);
                        return TransitionIds.TO_TIGHT;
                    }
                    return -1;
                }
                else {
                    LOG.debug("Target spread updated while waiting for TIGHT_SPREAD signal: secCode {}", m_security.code());
                    cancelTimeout();
                    return getStateTransitionBySpread();
                }
            }
        });
        
        builder.translateEvent(StateIds.WAITING_TIGHT_SPREAD, EventIds.TIME_OUT, new EventTranslator() {
            @Override
            public int translateToTransitionId(int eventId) throws Exception {
                LOG.debug("Timed out while waiting for TIGHT_SPREAD signal: secCode {}", m_security.code());
                return TransitionIds.ERROR;
            }
        });
        return builder;
    }

    public void onMmOrderBookUpdated(final long nanoOfDay, final int mmBidLevel, final int mmAskLevel, final int targetSpread, final boolean isTight) throws Exception {
        m_nanoOfDay = nanoOfDay;
        m_mmBidLevel = mmBidLevel;
        m_mmAskLevel = mmAskLevel;
        m_targetSpread = targetSpread;
        m_isTight = isTight;
        m_stateMachine.onEventReceived(EventIds.MM_ORDER_BOOK_UPDATED);
        m_prevMmAskLevel = m_mmAskLevel;
    }

    public void onTriggerUp(final long nanoOfDay) throws Exception {
        m_nanoOfDay = nanoOfDay;
        m_stateMachine.onEventReceived(EventIds.TRIGGER_UP);
    }
    
    private void onReceiveTimeout(final int scheduleId, final long scheduledNanoOfDay) throws Exception {
        if (scheduleId == m_scheduleId && scheduledNanoOfDay == m_scheduledNanoOfDay) {
            m_stateMachine.onEventReceived(EventIds.TIME_OUT);
        }
    }
    
    private void cancelTimeout() {
        m_scheduleId = -1;
        m_scheduledNanoOfDay = -1;
    }

    private boolean scheduleTimeout(final int scheduleId, final long timeout) {
        m_scheduleId = scheduleId;
        m_scheduledNanoOfDay = m_nanoOfDay + timeout;
        LOG.debug("Scheduled timeout: secCode {}, scheduleId {}, scheduledTimeOut {}", m_security.code(), box(scheduleId), box(m_scheduledNanoOfDay));
        return m_scheduler.scheduleTask(m_scheduleId, m_scheduledNanoOfDay, m_timeoutTask);
    }

    private int getStateTransitionBySpread() {
        if (m_mmBidLevel >= SpreadTable.SPREAD_TABLE_MIN_LEVEL && m_mmAskLevel >= m_mmBidLevel) {
            return m_isTight ? TransitionIds.TO_TIGHT : TransitionIds.ERROR;
        }
        return TransitionIds.ERROR;
    }
    
    private void updateIssuerLag(final long issuerResponseTime) throws Exception {
        if (m_handler != null) {
            m_handler.onIssuerLagUpdated(issuerResponseTime);
        }
    }
    
    private void updateIssuerSmoothing(final long issuerSmoothing) throws Exception {
        if (m_handler != null) {
            m_handler.onIssuerSmoothingUpdated(issuerSmoothing);
        }        
    }
    
    @Override
    public void registerHandler(final StrategySignalHandler handler) {
        if (handler instanceof IssuerResponseTimeHandler) {
            m_handler = (IssuerResponseTimeHandler)handler;
        }        
    }

    @Override
    public void unregisterHandler(final StrategySignalHandler handler) {
        if (m_handler == handler) {
            m_handler = null;
        }
    }    

    public void printStats() {
        LOG.info("Issuer response time statistics: secCode {}, numberOfTriggers {}, numberOfResponses {}, timeWaitingForResponse {}, numberOfFullResponse {}, timeInWideSpread {}",
                m_security.code(), box(m_numberOfTriggers), box(m_numberOfResponses), box(m_totalTimeWaitingForResponse), box(m_numberOfFullResponses), box(m_totalTimeInWideSpread));
    }

    @Override
    public void reset() {
        
    }
    
}
