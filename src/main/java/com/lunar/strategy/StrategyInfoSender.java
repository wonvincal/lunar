package com.lunar.strategy;

import com.lunar.entity.Security;
import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.EventType;
import com.lunar.message.io.sbe.EventValueType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.strategy.parameters.ParamsSbeEncodable;

public interface StrategyInfoSender {
    final public StrategyInfoSender NULL_PARAMS_SENDER = new StrategyInfoSender() {
        @Override
        public void sendStrategyType(final StrategyType strategyType, final MessageSinkRef sink) {
           
        }

        @Override
        public void sendStrategyType(final StrategyType strategyType, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum) {
            
        }

        @Override
        public void broadcastStrategyType(final StrategyType strategyType) {
            
        }
        
        @Override
        public void sendSwitch(final StrategySwitch strategySwitch, final MessageSinkRef sink) {
            
        }
        
        @Override
        public void sendSwitch(final StrategySwitch strategySwitch, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum) {
            
        }
        
        @Override
        public void broadcastSwitch(final StrategySwitch strategySwitch) {
            
        }
        
        @Override
        public void sendStrategyParams(final ParamsSbeEncodable params, final MessageSinkRef sink) {
            
        }
        
        @Override
        public void sendStrategyParams(final ParamsSbeEncodable params, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum) {
            
        }

        @Override
        public void broadcastStrategyParams(final ParamsSbeEncodable params) {
            
        }
        
        @Override
        public void broadcastStrategyParamsNoPersist(final ParamsSbeEncodable params) {
           
        }

        @Override
        public boolean broadcastStrategyParamsBatched(final ParamsSbeEncodable params) {
            return true;
        }

        @Override
        public boolean broadcastStrategyParamsNoPersistBatched(final ParamsSbeEncodable params) {
            return true;
        }

        @Override
        public boolean broadcastStrategyParamsNoPersistThrottled(final ParamsSbeEncodable params) {
            return true;
        }

        @Override
        public boolean sendEventBatched(final StrategyType strategyType, final Security warrant, final EventType eventType, final long nanoOfDay, final EventValueType valueType, final long longValue) {
           return true;
        }
        
        @Override
        public int broadcastAllBatched() {
            return 0;
        }
        
        @Override
        public int broadcastAllThrottled() {
            return 0;
        }

    };
    
    public void sendStrategyType(final StrategyType strategyType, final MessageSinkRef sink);
    public void sendStrategyType(final StrategyType strategyType, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum);
    public void broadcastStrategyType(final StrategyType strategyType);    
    
    public void sendSwitch(final StrategySwitch strategySwitch, final MessageSinkRef sink);
    public void sendSwitch(final StrategySwitch strategySwitch, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum);
    public void broadcastSwitch(final StrategySwitch strategySwitch);
    
    public void sendStrategyParams(final ParamsSbeEncodable params, final MessageSinkRef sink);    
    public void sendStrategyParams(final ParamsSbeEncodable params, final MessageSinkRef sink, final RequestSbeDecoder request, final int seqNum);
    public void broadcastStrategyParams(final ParamsSbeEncodable params);
    public void broadcastStrategyParamsNoPersist(final ParamsSbeEncodable params);
    
    public boolean broadcastStrategyParamsBatched(final ParamsSbeEncodable params);
    public boolean broadcastStrategyParamsNoPersistBatched(final ParamsSbeEncodable params);
    public boolean broadcastStrategyParamsNoPersistThrottled(final ParamsSbeEncodable params);
    
    public boolean sendEventBatched(final StrategyType strategyType, final Security warrant, final EventType eventType, final long nanoOfDay, final EventValueType valueType, final long longValue);
    
    public int broadcastAllBatched();
    public int broadcastAllThrottled();
}   