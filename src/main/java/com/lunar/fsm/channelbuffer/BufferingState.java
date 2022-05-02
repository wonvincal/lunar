package com.lunar.fsm.channelbuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.exception.OutsideOfBufferRangeException;
import com.lunar.fsm.channelbuffer.MessageAction.MessageActionType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

import org.agrona.DirectBuffer;

public class BufferingState extends State {
	private static final Logger LOG = LogManager.getLogger(BufferingState.class);
	
	@Override
	Transition enter(ChannelBufferContext context, StateTransitionEvent event) {
		return onEvent(context, event);
	}
	
	@Override
	State exit(ChannelBufferContext context, StateTransitionEvent event) {
		return this;
	}
	
	@Override
	Transition onEvent(ChannelBufferContext context, StateTransitionEvent event) {
		switch (event){
		case RECOVERED:
			return Transitions.in_BUFFERING_receive_RECOVERED;
		case FAIL:
			return Transitions.in_ANY_STATE_receive_FAIL;
		default:
			return Transitions.NO_TRANSITION;
		}
	}
	
	@Override
	Transition onIndividualSnapshot(ChannelBufferContext context, int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		LOG.info("Dropped individual snapshot [channelId:{}, channelSeq:{}]", channelId, channelSeq);
		action.actionType(MessageActionType.DROPPED_NO_ACTION);
		return Transitions.NO_TRANSITION;
	}

	@Override
	Transition onMessage(ChannelBufferContext context, int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		// Replay buffered messages
		try {
			context.storeMessage(channelId, channelSeq, buffer, offset, header, templateId);
			action.actionType(MessageActionType.STORED_NO_ACTION);
			if (channelSeq == context.expectedNextSeq()){
				return context.flush() ? onEvent(context, StateTransitionEvent.RECOVERED) : Transitions.NO_TRANSITION;
			}
		}
		catch (OutsideOfBufferRangeException e){
			// TODO - what is the current next step?
			LOG.error("Dropped message. Caught buffer overflow exception.", e);				
		}
		catch (Exception e) {
			LOG.error("Caught exception onMessage", e);
		}
		return Transitions.NO_TRANSITION;			
	}
	
	@Override
	Transition onSequentialSnapshot(ChannelBufferContext context, int channelId, long channelSeq, int snapshotSeq, BooleanType isLast, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		if (channelSeq < context.expectedNextSeq()){
			action.actionType(MessageActionType.DROPPED_NO_ACTION);
			LOG.info("Dropped sequential snapshot because the snapshot is too outdated [channelId:{}, channelSeq:{}, expectedNextSeq:{}]", channelId, channelSeq, context.expectedNextSeq());
			return Transitions.NO_TRANSITION;
		}
		if (snapshotSeq == context.expectedNextSnapshotSeq()){
			try {
				context.storeSnapshot(channelId, channelSeq, snapshotSeq, isLast, buffer, offset, header, templateId);
				action.actionType(MessageActionType.STORED_NO_ACTION);
				if (context.channelSeqOfCompletedSnapshot() >= context.expectedNextSeq()){
					return context.flush() ? onEvent(context, StateTransitionEvent.RECOVERED) : Transitions.NO_TRANSITION;
				}
			}
			catch (OutsideOfBufferRangeException e){
				// TODO - what is the current next step?
				LOG.error("Dropped sequential snapshot. Caught buffer overflow exception.", e);				
			}
			catch (Exception e) {
				LOG.error("Dropped sequential snapshot. Caught exception.", e);
			}				
		}
		return Transitions.NO_TRANSITION;
	}

}
