package com.lunar.fsm.channelbuffer;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.exception.OutsideOfBufferRangeException;
import com.lunar.fsm.channelbuffer.MessageAction.MessageActionType;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;

public class PassThruState extends State {
	private static final Logger LOG = LogManager.getLogger(PassThruState.class);
	
	@Override
	Transition enter(ChannelBufferContext context, StateTransitionEvent event) {
		return Transitions.NO_TRANSITION;
	}
	@Override
	State exit(ChannelBufferContext context, StateTransitionEvent event) {
		return this;
	}
	@Override
	Transition onEvent(ChannelBufferContext context, StateTransitionEvent event) {
		switch (event){
		case GAP_DETECTED:
			return Transitions.in_PASS_THRU_receive_GAP_DETECTED;
		case MESSAGE_NOT_RECEIVED:
			return Transitions.in_PASS_THRU_receive_MESSAGE_NOT_RECEIVED;
		case FAIL:
			return Transitions.in_ANY_STATE_receive_FAIL;
		default:
			return Transitions.NO_TRANSITION;
		}
	}
	
	@Override
	Transition onIndividualSnapshot(ChannelBufferContext context, int channelId, long channelSnapshotSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		LOG.info("Dropped individual snapshot [channelId:{}, channelSnapshotSeq:{}]", channelId, channelSnapshotSeq);
		action.actionType(MessageActionType.DROPPED_NO_ACTION);
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	Transition onMessage(ChannelBufferContext context, int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		long expectedNextSeq = context.expectedNextSeq();
		if (channelSeq == expectedNextSeq){
			context.expectedNextSeq(expectedNextSeq + 1);
			action.actionType(MessageActionType.SEND_NOW);
		}
		else if (channelSeq > expectedNextSeq){
			action.actionType(MessageActionType.STORED_NO_ACTION);
			try {
				byte senderSinkId = header.senderSinkId();
				context.storeMessageAndSetMissingSequence(channelId, expectedNextSeq, channelSeq, buffer, offset, header, templateId);
				context.requestMessage(channelId, expectedNextSeq, expectedNextSeq, expectedNextSeq, senderSinkId);
				return onEvent(context, StateTransitionEvent.GAP_DETECTED);
			}
			catch (OutsideOfBufferRangeException e){
				// TODO - what to do next?
				LOG.error("Dropped message because buffer overflow", e);
				return Transitions.NO_TRANSITION;
			}
		}
		else {
			action.actionType(MessageActionType.DROPPED_NO_ACTION);
			LOG.warn("Drop message with old sequence [channelId:{}, channelSeq:{}, expectedNextSeq:{}]", channelId, channelSeq, expectedNextSeq);
		}
		return Transitions.NO_TRANSITION;
	}
	
	@Override
	Transition onMessageNotReceived(ChannelBufferContext context, int channelId, long fromSeq, long toSeq, int senderSinkId) {
		context.requestMessage(channelId, context.expectedNextSeq(), fromSeq, toSeq, senderSinkId);
		return onEvent(context, StateTransitionEvent.MESSAGE_NOT_RECEIVED);
	}
	
	@Override
	Transition onSequentialSnapshot(ChannelBufferContext context, int channelId, long channelSeq, int snapshotseq, BooleanType isLast, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action) {
		LOG.info("Dropped sequential snapshot [channelId:{}, channelSeq:{}, snapshotSeq:{}]", channelId, channelSeq, snapshotseq);
		action.actionType(MessageActionType.DROPPED_NO_ACTION);
		return Transitions.NO_TRANSITION;
	}
}
