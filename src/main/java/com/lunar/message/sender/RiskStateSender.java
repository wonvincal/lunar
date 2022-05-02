package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.RiskControlSetting;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.RiskControlType;
import com.lunar.message.io.sbe.RiskStateSbeEncoder;
import com.lunar.message.io.sbe.RiskStateSbeEncoder.ControlsEncoder;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.position.RiskState;
import com.lunar.service.ServiceConstant;

public class RiskStateSender {
	static final Logger LOG = LogManager.getLogger(RiskStateSender.class);
	private final MessageSender msgSender;
	private final RiskStateSbeEncoder sbe = new RiskStateSbeEncoder();

	public static RiskStateSender of(MessageSender msgSender){
		return new RiskStateSender(msgSender);
	}
	
	RiskStateSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}

	public long sendRiskState(MessageSinkRef sink, 
			EntityType positionEntityType,
			long positionEntitySid,
			RiskControlType riskControlType,
			double current,
			double previous,
			boolean exceeded){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) >= 0L){
			encodeRiskEvent(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe,
					positionEntityType,
					positionEntitySid,
					riskControlType,
					current,
					previous,
					exceeded);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeRiskEvent(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe,
				positionEntityType,
				positionEntitySid,
				riskControlType,
				current,
				previous,
				exceeded);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public long sendRiskState(MessageSinkRef[] sinks,
			int numSinks,
			RiskState state,
			long[] results){
		int size = encodeRiskState(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				sbe,
				state);
		return msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, size, results);
	}
	
	public long sendRiskState(MessageSinkRef[] sinks,
			int numSinks,
			EntityType positionEntityType,
			long positionEntitySid,
			RiskControlType riskControlType,
			double current,
			double previous,
			boolean exceeded,
			long[] results){
		int size = encodeRiskEvent(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				sbe,
				positionEntityType,
				positionEntitySid,
				riskControlType,
				current,
				previous,
				exceeded);
		return msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, size, results);
	}
	
	private int expectedEncodedLength(){
		final int numParameters = RiskControlSetting.NUM_CONTROLS;
		int size = MessageHeaderEncoder.ENCODED_LENGTH + 
				RiskStateSbeEncoder.BLOCK_LENGTH + 
				RiskStateSbeEncoder.ControlsEncoder.sbeHeaderSize() + 
				RiskStateSbeEncoder.ControlsEncoder.sbeBlockLength() * numParameters;
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + size + ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}

	public static int encodeRiskEvent(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			RiskStateSbeEncoder sbe,
			EntityType entityType,
			long entitySid,
			RiskControlType riskControlType,
			double current,
			double previous,
			boolean exceeded){
		int payloadLength = encodeRiskStateOnly(buffer, 
				offset + sender.headerSize(), 
				sender.stringBuffer(), 
				sbe, 
				entityType,
				entitySid,
				riskControlType,
				current,
				previous, 
				exceeded);
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				RiskStateSbeEncoder.BLOCK_LENGTH, 
				RiskStateSbeEncoder.TEMPLATE_ID, 
				RiskStateSbeEncoder.SCHEMA_ID, 
				RiskStateSbeEncoder.SCHEMA_VERSION,
				payloadLength);

		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeRiskState(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			RiskStateSbeEncoder sbe,
			RiskState state){
		int payloadLength = encodeRiskStateOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				state);
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				RiskStateSbeEncoder.BLOCK_LENGTH, 
				RiskStateSbeEncoder.TEMPLATE_ID, 
				RiskStateSbeEncoder.SCHEMA_ID, 
				RiskStateSbeEncoder.SCHEMA_VERSION,
				payloadLength);

		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeRiskStateOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			final MutableDirectBuffer stringBuffer,
			RiskStateSbeEncoder sbe,
			EntityType entityType,
			long entitySid,
			RiskControlType riskControlType,
			double current,
			double previous,
			boolean exceeded){
		sbe.wrap(dstBuffer, offset)
		.entityType(entityType)
		.entitySid(entitySid)
		.controlsCount(1).next()
		.type(riskControlType)
		.current(current)
		.previous(previous)
		.exceeded(exceeded ? BooleanType.TRUE : BooleanType.FALSE);
		return sbe.encodedLength();		
	}

	public static int encodeRiskStateOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			RiskStateSbeEncoder sbe,
			RiskState state){
		sbe.wrap(dstBuffer, offset)
		.entityType(state.entityType())
		.entitySid(state.entitySid())
		.controlsCount(RiskControlSetting.NUM_CONTROLS)
		.next()
		.type(RiskControlType.MAX_OPEN_POSITION)
		.current(state.openPosition())
		.previous(ControlsEncoder.previousNullValue())
		.exceeded(state.exceededMaxOpenPosition() ? BooleanType.TRUE : BooleanType.FALSE)
		.next()
		.type(RiskControlType.MAX_PROFIT)
		.current(state.totalPnl())
		.previous(ControlsEncoder.previousNullValue())
		.exceeded(state.exceededMaxProfit() ? BooleanType.TRUE : BooleanType.FALSE)
		.next()
		.type(RiskControlType.MAX_LOSS)
		.current(state.totalPnl())
		.previous(ControlsEncoder.previousNullValue())
		.exceeded(state.exceededMaxLoss() ? BooleanType.TRUE : BooleanType.FALSE)
		.next()
		.type(RiskControlType.MAX_CAP_LIMIT)
		.current(state.maxCapUsed())
		.previous(ControlsEncoder.previousNullValue())
		.exceeded(state.exceededMaxCapLimit() ? BooleanType.TRUE : BooleanType.FALSE);
		return sbe.encodedLength();		
	}

}

