package com.lunar.strategy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.core.SubscriberList;
import com.lunar.core.SystemClock;
import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EventCategory;
import com.lunar.message.io.sbe.EventLevel;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.parameters.ParamsSbeEncodable;

public class LunarStrategyInfoSender implements StrategyInfoSender {
    private static final Logger LOG = LogManager.getLogger(LunarStrategyInfoSender.class);

    
    private final Messenger messenger;
    @SuppressWarnings("unused")
    private final SystemClock systemClock;
    private long sentCounter = 0;
    
    private class ParamsSendInfo {
        ParamsSbeEncodable params;
    }
    
    private class EventSendInfo {
        EventType eventType;
        @SuppressWarnings("unused")
        long strategyId;
        long securitySid;
        long nanoOfDay;
        EventValueType eventValueType;
        long longValue;
    }
    
    private static final int MAX_PARAMS_TO_SEND = 1024;
    private static final int MAX_PARAMS_TO_THROTTLE = 8192;
    private static final int THROTTLE_INDEX_MASK = 8192 - 1;
    private static final int MAX_EVENTS_TO_SEND = 256;
    private static final int MAX_PARAMS_TO_SEND_PER_THROTTLED_BATCH = 128;

    private final ParamsSendInfo[] paramsToSendList = new ParamsSendInfo[MAX_PARAMS_TO_SEND];
    private final ParamsSendInfo[] paramsToSendWithPersistList = new ParamsSendInfo[MAX_PARAMS_TO_SEND];
    private final ParamsSendInfo[] paramsToThrottleRingBuffer = new ParamsSendInfo[MAX_PARAMS_TO_THROTTLE];
    
    private final EventSendInfo[] eventsToSendList = new EventSendInfo[MAX_EVENTS_TO_SEND];
    private final SubscriberList subscriberList;
    
    private int paramsToSendCount = 0;
    private int paramsToSendWithPersistCount = 0;
    private int paramsToSendToThrottleCount = 0;
    private int paramsToSendToThrottleStartIndex = 0;
    
    private int eventsToSendCount = 0;

    public LunarStrategyInfoSender(final Messenger messenger, final SystemClock systemClock, final SubscriberList subscriberList) {        
        this.messenger = messenger;
        this.systemClock = systemClock;
        for (int i = 0; i < MAX_PARAMS_TO_SEND; i++) {
            paramsToSendList[i] = new ParamsSendInfo();
            paramsToSendWithPersistList[i] = new ParamsSendInfo();
        }
        for (int i = 0; i < MAX_PARAMS_TO_THROTTLE; i++) {
            paramsToThrottleRingBuffer[i] = new ParamsSendInfo();
        }
        for (int i = 0; i < MAX_EVENTS_TO_SEND; i++) {
            eventsToSendList[i] = new EventSendInfo();
        }
        this.subscriberList = subscriberList;
    }
    
    @Override
    public void sendStrategyType(final StrategyType strategyType, final MessageSinkRef sink) {
        messenger.strategySender().sendSbeEncodable(sink, strategyType);
    }
    
    @Override
    public void sendStrategyType(final StrategyType strategyType, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum) {
        messenger.responseSender().sendSbeEncodable(sink, request.clientKey(), BooleanType.FALSE, seqNum, ResultType.OK, strategyType);
    }
    
    @Override
    public void broadcastStrategyType(final StrategyType strategyType) {
        messenger.strategySender().sendSbeEncodable(subscriberList.elements(), strategyType);
    }
    
    @Override
    public void sendSwitch(final StrategySwitch strategySwitch, final MessageSinkRef sink) {
        messenger.strategySender().sendSbeEncodable(sink, strategySwitch);
    }
    
    @Override
    public void sendSwitch(final StrategySwitch strategySwitch, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum) {
        messenger.responseSender().sendSbeEncodable(sink, request.clientKey(), BooleanType.FALSE, seqNum, ResultType.OK, strategySwitch);
    }
    
    @Override
    public void broadcastSwitch(final StrategySwitch strategySwitch) {
        messenger.strategySender().sendSbeEncodable(messenger.referenceManager().persi(), subscriberList.elements(), strategySwitch);
    }
    
    @Override
    public void sendStrategyParams(final ParamsSbeEncodable params, final MessageSinkRef sink) {
        messenger.strategySender().sendSbeEncodable(sink, params);
    }    

    @Override
    public void sendStrategyParams(final ParamsSbeEncodable params, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum) {
        messenger.responseSender().sendSbeEncodable(sink, request.clientKey(), BooleanType.FALSE, seqNum, ResultType.OK, params);
    }

    @Override
    public void broadcastStrategyParams(final ParamsSbeEncodable params) {
        messenger.strategySender().sendSbeEncodable(messenger.referenceManager().persi(), subscriberList.elements(), params);
    }
    
    @Override
    public void broadcastStrategyParamsNoPersist(final ParamsSbeEncodable params) {
        messenger.strategySender().sendSbeEncodable(subscriberList.elements(), params);
    }
    
    @Override
    public boolean broadcastStrategyParamsBatched(final ParamsSbeEncodable params) {
        if (paramsToSendWithPersistCount < MAX_PARAMS_TO_SEND) {
            final ParamsSendInfo sendInfo = paramsToSendWithPersistList[paramsToSendWithPersistCount++];
            sendInfo.params = params;
            return true;
        }
        LOG.warn("Cannot add strategy params to batch because batch size is exceeded");
        return false;
    }
    
    @Override
    public boolean broadcastStrategyParamsNoPersistBatched(final ParamsSbeEncodable params) {
        if (paramsToSendCount < MAX_PARAMS_TO_SEND) {
            final ParamsSendInfo sendInfo = paramsToSendList[paramsToSendCount++];
            sendInfo.params = params;
            return true;
        }
        LOG.warn("Cannot add strategy params no persist to batch because batch size is exceeded");
        return false;
    }
    
    @Override
    public boolean broadcastStrategyParamsNoPersistThrottled(final ParamsSbeEncodable params) {
        if (paramsToSendToThrottleCount == MAX_PARAMS_TO_THROTTLE) {
            LOG.warn("Overwritting stale throttled strategy params because batch size is exceeded");
            paramsToSendToThrottleStartIndex = (paramsToSendToThrottleStartIndex + 1) & THROTTLE_INDEX_MASK;
            paramsToSendToThrottleCount--;
        }
        final ParamsSendInfo sendInfo = paramsToThrottleRingBuffer[(paramsToSendToThrottleStartIndex + paramsToSendToThrottleCount++) & THROTTLE_INDEX_MASK];
        sendInfo.params = params;
        return true;
    }    

    @Override
    public boolean sendEventBatched(StrategyType strategyType, Security warrant, EventType eventType, final long nanoOfDay, final EventValueType eventValueType, final long longValue) {
        if (eventsToSendCount < MAX_EVENTS_TO_SEND) {
            final EventSendInfo sendInfo = eventsToSendList[eventsToSendCount++];
            sendInfo.eventType = eventType;
            sendInfo.strategyId = strategyType.sid();
            sendInfo.securitySid = warrant.sid();
            sendInfo.nanoOfDay = nanoOfDay;
            sendInfo.eventValueType = eventValueType;
            sendInfo.longValue = longValue;
            return true;
        }
        LOG.warn("Cannot add event to batch because batch size is exceeded");
        return false;        
    }
    
    @Override
    public int broadcastAllBatched() {
        final int totalCount = paramsToSendCount + paramsToSendWithPersistCount;
        if (totalCount > 0) {            
            // no need to use timestamp, a counter will do. we just want to prevent same parameter from being sent multiple times
            final long timeStamp = sentCounter++; //systemClock.timestamp();
            for (int i = 0; i < paramsToSendWithPersistCount; i++) {
                final ParamsSendInfo sendInfo = paramsToSendWithPersistList[i];
                if (sendInfo.params.lastSendTime() < timeStamp) {
                    messenger.strategySender().sendSbeEncodable(messenger.referenceManager().persi(), subscriberList.elements(), sendInfo.params);
                    sendInfo.params.lastSendTime(timeStamp);
                }
            }
            paramsToSendWithPersistCount = 0;
            for (int i = 0; i < paramsToSendCount; i++) {
                final ParamsSendInfo sendInfo = paramsToSendList[i];
                if (sendInfo.params.lastSendTime() < timeStamp) {
                    messenger.strategySender().sendSbeEncodable(subscriberList.elements(), sendInfo.params);
                    sendInfo.params.lastSendTime(timeStamp);
                }
            }
            paramsToSendCount = 0;
            return totalCount;
        }
        if (eventsToSendCount > 0) {
            for (int i = 0; i < eventsToSendCount; i++) {
                final EventSendInfo sendInfo = eventsToSendList[i];
                messenger.eventSender().sendEventWithTwoValues(messenger.referenceManager().ns(), EventCategory.STRATEGY, EventLevel.INFO, sendInfo.eventType, messenger.self().sinkId(),
                        sendInfo.nanoOfDay, "Strategy signal received", EventValueType.SECURITY_SID, sendInfo.securitySid, sendInfo.eventValueType, sendInfo.longValue);
            }
            eventsToSendCount = 0;
        }
        return 0;
    }
    
    @Override
    public int broadcastAllThrottled() {
        if (paramsToSendToThrottleCount > 0) {
            final int count = Math.min(paramsToSendToThrottleCount, MAX_PARAMS_TO_SEND_PER_THROTTLED_BATCH);
            // no need to use timestamp, a counter will do. we just want to prevent same parameter from being sent multiple times
            final long timeStamp = sentCounter++; //systemClock.timestamp();
            for (int i = 0; i < count; i++) {
                final ParamsSendInfo sendInfo = paramsToThrottleRingBuffer[(paramsToSendToThrottleStartIndex + i) & THROTTLE_INDEX_MASK];
                if (sendInfo.params.lastSendTime() < timeStamp) {
                    messenger.strategySender().sendSbeEncodable(subscriberList.elements(), sendInfo.params);
                    sendInfo.params.lastSendTime(timeStamp);
                }
            }
            paramsToSendToThrottleStartIndex = (paramsToSendToThrottleStartIndex + count) & THROTTLE_INDEX_MASK;
            paramsToSendToThrottleCount -= count;
            return count;
        }
        return 0;
    }

}
