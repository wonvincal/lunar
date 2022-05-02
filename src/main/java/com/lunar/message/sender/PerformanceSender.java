package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.MultiThreadSubscriberList;
import com.lunar.core.SubscriberList;
import com.lunar.message.binary.MessageEncoder;
import com.lunar.message.io.sbe.GenericTrackerSbeEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.TrackerStepType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;

import org.agrona.MutableDirectBuffer;

public class PerformanceSender {
    static final Logger LOG = LogManager.getLogger(PerformanceSender.class);

    private final MessageSender msgSender;    
    private final GenericTrackerSbeEncoder genericTrackerSbe = new GenericTrackerSbeEncoder();  

    /**
     * Create a PerformanceSender with a specific MessageSender
     * @param msgSender
     * @return
     */
    public static PerformanceSender of(MessageSender msgSender){
        return new PerformanceSender(msgSender);
    }
    
    PerformanceSender(MessageSender msgSender){
        this.msgSender = msgSender;
    }
    
    public long sendGenericTracker(final MessageSinkRef sink, final byte senderSink, final TrackerStepType stepId, final byte triggeredBy, final int triggerSeqNum, final long triggerNanoOfDay, final long timestamp) {
        MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
        if (msgSender.tryClaim(MessageHeaderEncoder.ENCODED_LENGTH + GenericTrackerSbeEncoder.BLOCK_LENGTH, sink, bufferClaim) == MessageSink.OK) {
            encodeGenericTracker(msgSender,
                    sink.sinkId(), 
                    bufferClaim.buffer(),
                    bufferClaim.offset(),
                    genericTrackerSbe,
                    senderSink,
                    stepId,
                    triggeredBy,
                    triggerSeqNum,
                    triggerNanoOfDay,
                    timestamp);
            bufferClaim.commit();
            return MessageSink.OK;
        }
        int size = encodeGenericTracker(msgSender,
                sink.sinkId(),
                msgSender.buffer(),
                0,
                genericTrackerSbe,
                senderSink,
                stepId,
                triggeredBy,
                triggerSeqNum,
                triggerNanoOfDay,
                timestamp);
        return msgSender.trySend(sink, msgSender.buffer(), 0, size);
    }
    
    public void sendGenericTracker(MultiThreadSubscriberList subscribers, final byte senderSink, final TrackerStepType stepId, final byte triggeredBy, final int triggerSeqNum, final long triggerNanoOfDay, final long timestamp) {
        int size = encodeGenericTracker(msgSender,
                0,
                msgSender.buffer(),
                0,
                genericTrackerSbe,
                senderSink,
                stepId,
                triggeredBy,
                triggerSeqNum,
                triggerNanoOfDay,
                timestamp);
        MessageSinkRef[] sinks = subscribers.elements();
        for (MessageSinkRef sink : sinks){
        	if (sink == null){
        		break;
        	}
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sink.sinkId());
            msgSender.trySend(sink, msgSender.buffer(), 0, size);          
        }
    }    

    public void sendGenericTracker(SubscriberList subscribers, final byte senderSink, final TrackerStepType stepId, final byte triggeredBy, final int triggerSeqNum, final long triggerNanoOfDay, final long timestamp) {
        int size = encodeGenericTracker(msgSender,
                0,
                msgSender.buffer(),
                0,
                genericTrackerSbe,
                senderSink,
                stepId,
                triggeredBy,
                triggerSeqNum,
                triggerNanoOfDay,
                timestamp);
        MessageSinkRef[] sinks = subscribers.elements();
        for (int i = 0; i < subscribers.size(); i++){
            MessageEncoder.encodeDstSinkId(msgSender.buffer(), 0, msgSender.header(), sinks[i].sinkId());
            msgSender.trySend(sinks[i], msgSender.buffer(), 0, size);          
        }
    }    

    static public int encodeGenericTracker(final MessageSender sender,
            final int dstSinkId,
            final MutableDirectBuffer buffer,
            final int offset,
            final GenericTrackerSbeEncoder sbe,
            final byte senderSink,
            final TrackerStepType stepId, 
            final byte triggeredBy,
            final int triggerSeqNum,
            final long triggerNanoOfDay,
            final long timestamp) {
    	int payloadLength  = encodeGenericTrackerWithoutHeader(buffer, offset + sender.headerSize(), sbe, senderSink, stepId, triggeredBy, triggerSeqNum, triggerNanoOfDay, timestamp);
        sender.encodeHeader(dstSinkId,
                buffer,
                offset,
                GenericTrackerSbeEncoder.BLOCK_LENGTH,
                GenericTrackerSbeEncoder.TEMPLATE_ID,
                GenericTrackerSbeEncoder.SCHEMA_ID,
                GenericTrackerSbeEncoder.SCHEMA_VERSION,
                payloadLength);
        return sender.headerSize() + payloadLength; 
    } 

    static public int encodeGenericTrackerWithoutHeader(final MutableDirectBuffer buffer,
            final int offset,
            final GenericTrackerSbeEncoder sbe,
            final byte senderSink,
            final TrackerStepType stepId,
            final byte triggeredBy,
            final int triggerSeqNum,
            final long triggerNanoOfDay,
            final long timestamp) {
        sbe.wrap(buffer,  offset).sendBy(senderSink).stepId(stepId).timestamp(timestamp).triggerInfo().triggerSeqNum(triggerSeqNum).triggeredBy(triggeredBy).nanoOfDay(triggerNanoOfDay);
        return sbe.encodedLength();
    }    
    
}
