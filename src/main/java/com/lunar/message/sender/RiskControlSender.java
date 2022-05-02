package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.entity.RiskControlSetting;
import com.lunar.journal.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.RiskControlSbeEncoder;
import com.lunar.message.io.sbe.RiskControlSbeEncoder.ControlsEncoder;
import com.lunar.message.io.sbe.RiskControlType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class RiskControlSender {
	static final Logger LOG = LogManager.getLogger(RiskControlSender.class);
	private final MessageSender msgSender;
	private final RiskControlSbeEncoder sbe = new RiskControlSbeEncoder();

	public static RiskControlSender of(MessageSender msgSender){
		return new RiskControlSender(msgSender);
	}
	
	RiskControlSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}

	public long sendRiskControl(MessageSinkRef sink,
			RiskControlSetting setting){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(), sink, bufferClaim) >= 0L){
			encodeRiskControl(msgSender,
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe,
					setting);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeRiskControl(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe,
				setting);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	private int expectedEncodedLength(){
		final int numParameters = RiskControlSetting.NUM_CONTROLS;
		int size = MessageHeaderEncoder.ENCODED_LENGTH + 
				RiskControlSbeEncoder.BLOCK_LENGTH + 
				RiskControlSbeEncoder.ControlsEncoder.sbeHeaderSize() + 
				RiskControlSbeEncoder.ControlsEncoder.sbeBlockLength() * numParameters;
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + size + ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}

	public static int encodeRiskControl(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			RiskControlSbeEncoder sbe,
			RiskControlSetting setting){
		int payloadLength = encodeRiskControlOnly(buffer, 
				offset + sender.headerSize(), 
				sbe, 
				setting);
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				RiskControlSbeEncoder.BLOCK_LENGTH, 
				RiskControlSbeEncoder.TEMPLATE_ID, 
				RiskControlSbeEncoder.SCHEMA_ID, 
				RiskControlSbeEncoder.SCHEMA_VERSION,
				payloadLength);

		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodeRiskControlOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			RiskControlSbeEncoder sbe,
			RiskControlSetting setting){
		int numControl = 0;
		if (setting.maxOpenPosition().isPresent()){
			numControl++;
		}
		if (setting.maxLoss().isPresent()){
			numControl++;
		}
		if (setting.maxProfit().isPresent()){
			numControl++;
		}
		ControlsEncoder controlsEncoder = sbe.wrap(dstBuffer, offset)
		.entityType(setting.entityType())
		.toAllEntity(setting.allEntity() ? BooleanType.TRUE : BooleanType.FALSE)
		.specificEntitySid(setting.specificEntitySid())
		.controlsCount(numControl);
		if (setting.maxOpenPosition().isPresent()){
			controlsEncoder.next().type(RiskControlType.MAX_OPEN_POSITION).value(setting.maxOpenPosition().get());
		}
		if (setting.maxLoss().isPresent()){
			controlsEncoder.next().type(RiskControlType.MAX_LOSS).value(setting.maxLoss().get());
		}
		if (setting.maxProfit().isPresent()){
			controlsEncoder.next().type(RiskControlType.MAX_PROFIT).value(setting.maxProfit().get());
		}
		return sbe.encodedLength();	
	}
}
