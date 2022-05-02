package com.lunar.fsm.channelbuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.fsm.channelbuffer.MessageAction.MessageActionType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

import org.agrona.DirectBuffer;

/**
 * The goal is to get the first Message, so that we can determine the next sequence number 
 * @author wongca
 *
 */
public class InitState extends State {
	private static final Logger LOG = LogManager.getLogger(ChannelBufferContext.class);
	@Override
	Transition enter(ChannelBufferContext context, StateTransitionEvent event) {
		context.init(true);
		return onEvent(context, event);
	}
	@Override
	State exit(ChannelBufferContext context, StateTransitionEvent event) {
		return this;
	}
	
	@Override
	Transition onEvent(ChannelBufferContext context, StateTransitionEvent event) {
		switch (event){
		case START_SEQ_DETECTED:
			return Transitions.in_INIT_receive_START_SEQ_DETECTED;
		case FAIL:
			return Transitions.in_ANY_STATE_receive_FAIL;
		default:
			return Transitions.NO_TRANSITION;
		}
	}
	
	@Override
	Transition onIndividualSnapshot(ChannelBufferContext context, int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		LOG.info("Dropped individual snapshot [channelId:{}, channelSnapshotSeq:{}]", channelId, channelSeq);
		action.actionType(MessageActionType.DROPPED_NO_ACTION);
		return Transitions.NO_TRANSITION;
	}
	@Override
	Transition onMessage(ChannelBufferContext context, int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		// Successfully receive first message
		context.expectedNextSeq(channelSeq + 1);
		action.actionType(MessageActionType.SEND_NOW);
		return onEvent(context, StateTransitionEvent.START_SEQ_DETECTED);
	}
	@Override
	Transition onSequentialSnapshot(ChannelBufferContext context, int channelId, long channelSeq, int snapshotSeq, BooleanType isLast, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		LOG.info("Dropped sequential snapshot [channelId:{}, channelSeq:{}, snapshotSeq:{}]", channelId, channelSeq, snapshotSeq);
		action.actionType(MessageActionType.DROPPED_NO_ACTION);
		return Transitions.NO_TRANSITION;
	}
}

