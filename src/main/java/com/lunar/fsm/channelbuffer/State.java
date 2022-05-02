package com.lunar.fsm.channelbuffer;

import static com.lunar.fsm.channelbuffer.Transitions.in_ANY_STATE_receive_FAIL;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.ResultType;

import org.agrona.DirectBuffer;

public abstract class State {
	Transition enter(ChannelBufferContext context, StateTransitionEvent event){
		return in_ANY_STATE_receive_FAIL;
	}
	State exit(ChannelBufferContext context, StateTransitionEvent event){
		return this;
	}
	Transition onEvent(ChannelBufferContext context, StateTransitionEvent event){
		return in_ANY_STATE_receive_FAIL;
	}
	Transition onMessage(ChannelBufferContext context, int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action){
		return in_ANY_STATE_receive_FAIL;
	}
	Transition onMessageNotReceived(ChannelBufferContext context, int channelId, long fromChannelSeq, long toChannelSeq, int senderSinkId){
		return in_ANY_STATE_receive_FAIL;
	}
	Transition onSequentialSnapshot(ChannelBufferContext context, int channelId, long channelSeq, int snapshotSeq, BooleanType isLast, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action){
		return in_ANY_STATE_receive_FAIL;
	}
	Transition onIndividualSnapshot(ChannelBufferContext context, int channelId, long channelSeq, DirectBuffer buffer, int offset, MessageHeaderDecoder header, int templateId, MessageAction action){
		return in_ANY_STATE_receive_FAIL;
	}
	Transition onMissingMessageRequestFailure(ChannelBufferContext context, ResultType resultType){
		return in_ANY_STATE_receive_FAIL;
	}
	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}
}
