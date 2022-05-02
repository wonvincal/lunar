package com.lunar.message.sender;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.PositionSbeEncoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.position.PositionDetails;
import com.lunar.position.PositionLoader;
import com.lunar.position.SecurityPositionDetails;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class PositionSender {
	static final Logger LOG = LogManager.getLogger(PositionSender.class);
	private final MessageSender msgSender;
	private final PositionSbeEncoder sbe = new PositionSbeEncoder();

	public static PositionSender of(MessageSender msgSender){
		return new PositionSender(msgSender);
	}
	
	PositionSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}

	public long sendPosition(MessageSinkRef[] sinks,
			int numSinks,
			SecurityPositionDetails details,
			long[] results){
		int size = encodePosition(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				sbe,
				details);
		return msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, size, results);
	}

	public long sendPosition(MessageSinkRef[] sinks,
			int numSinks,
			PositionDetails details,
			long[] results){
		int size = encodePosition(msgSender, 
				ServiceConstant.SERVICE_ID_NOT_APPLICABLE, 
				msgSender.buffer(), 
				0, 
				sbe,
				details);
		return msgSender.trySend(sinks, numSinks, msgSender.buffer(), 0, size, results);
	}

	@SuppressWarnings("unused")
	private int expectedEncodedLength(){
		if (MessageHeaderEncoder.ENCODED_LENGTH + PositionSbeEncoder.BLOCK_LENGTH > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + PositionSbeEncoder.BLOCK_LENGTH + 
					") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return MessageHeaderEncoder.ENCODED_LENGTH + PositionSbeEncoder.BLOCK_LENGTH;
	}

	public static int encodePosition(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			PositionSbeEncoder sbe, 
			PositionDetails details){
		int payloadLength = encodePositionOnly(buffer, 
				offset + sender.headerSize(), 
				sender.stringBuffer(), 
				sbe, 
				details);
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				PositionSbeEncoder.BLOCK_LENGTH, 
				PositionSbeEncoder.TEMPLATE_ID, 
				PositionSbeEncoder.SCHEMA_ID, 
				PositionSbeEncoder.SCHEMA_VERSION,
				payloadLength);

		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodePosition(MessageSender sender,
			int dstSinkId,
			final MutableDirectBuffer buffer, 
			int offset,
			PositionSbeEncoder sbe, 
			SecurityPositionDetails details){
		int payloadLength = encodeSecurityPositionOnly(buffer, 
				offset + sender.headerSize(), 
				sender.stringBuffer(), 
				sbe, 
				details);
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				PositionSbeEncoder.BLOCK_LENGTH, 
				PositionSbeEncoder.TEMPLATE_ID, 
				PositionSbeEncoder.SCHEMA_ID, 
				PositionSbeEncoder.SCHEMA_VERSION,
				payloadLength);

		return sender.headerSize() + payloadLength; 
	}
	
	public static int encodePositionOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			PositionSbeEncoder sbe,
			PositionLoader.Position position){
		sbe.wrap(dstBuffer, offset)
		.avgBuyPrice(PositionSbeEncoder.avgBuyPriceNullValue())
		.avgSellPrice(PositionSbeEncoder.avgSellPriceNullValue())
		.entitySid(position.secSid())
		.entityType(EntityType.SECURITY)
		.mtmSellPrice(PositionSbeEncoder.mtmSellPriceNullValue())
		.mtmBuyPrice(PositionSbeEncoder.mtmBuyPriceNullValue())
		.openPosition(position.openPosition())
		.openCallPosition(PositionSbeEncoder.openCallPositionNullValue())
		.openPutPosition(PositionSbeEncoder.openPutPositionNullValue())
		.osBuyQty(PositionSbeEncoder.osBuyQtyNullValue())
		.osBuyNotional(PositionSbeEncoder.osBuyNotionalNullValue())
		.osSellQty(PositionSbeEncoder.osSellQtyNullValue())
		.osSellNotional(PositionSbeEncoder.osSellNotionalNullValue())
		.buyQty(PositionSbeEncoder.buyQtyNullValue())
		.buyNotional(PositionSbeEncoder.buyNotionalNullValue())
		.sellQty(PositionSbeEncoder.sellQtyNullValue())
		.sellNotional(PositionSbeEncoder.sellNotionalNullValue())
		.capUsed(PositionSbeEncoder.capUsedNullValue())
		.maxCapUsed(PositionSbeEncoder.maxCapUsedNullValue())
		.fees(PositionSbeEncoder.feesNullValue())
		.commission(PositionSbeEncoder.commissionNullValue())
		.netRealizedPnl(PositionSbeEncoder.netRealizedPnlNullValue())
		.unrealizedPnl(PositionSbeEncoder.unrealizedPnlNullValue())
		.totalPnl(PositionSbeEncoder.totalPnlNullValue())
		.tradeCount(PositionSbeEncoder.tradeCountNullValue())
		.experimentalNetRealizedPnl(PositionSbeEncoder.experimentalNetRealizedPnlNullValue())
		.experimentalUnrealizedPnl(PositionSbeEncoder.experimentalUnrealizedPnlNullValue())
		.experimentalTotalPnl(PositionSbeEncoder.experimentalTotalPnlNullValue());
		return sbe.encodedLength();		
	}
	
	public static int encodePositionOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			final MutableDirectBuffer stringBuffer,
			PositionSbeEncoder sbe,
			PositionDetails details){
		sbe.wrap(dstBuffer, offset)
		.avgBuyPrice(PositionSbeEncoder.avgBuyPriceNullValue())
		.avgSellPrice(PositionSbeEncoder.avgSellPriceNullValue())
		.entitySid(details.entitySid())
		.entityType(details.entityType())
		.mtmSellPrice(PositionSbeEncoder.mtmSellPriceNullValue())
		.mtmBuyPrice(PositionSbeEncoder.mtmBuyPriceNullValue())
		.openPosition(details.openPosition())		
		.openCallPosition(details.openCallPosition())
		.openPutPosition(details.openPutPosition())
		.osBuyQty(PositionSbeEncoder.osBuyQtyNullValue())
		.osBuyNotional(PositionSbeEncoder.osBuyNotionalNullValue())
		.osSellQty(PositionSbeEncoder.osSellQtyNullValue())
		.osSellNotional(PositionSbeEncoder.osSellNotionalNullValue())
		.buyQty(details.buyQty())
		.buyNotional(details.buyNotional())
		.sellQty(details.sellQty())
		.sellNotional(details.sellNotional())
		.capUsed(details.capUsed())
		.maxCapUsed(details.maxCapUsed())
		.fees(details.fees())
		.commission(details.commission())
		.netRealizedPnl(details.netRealizedPnl())
		.unrealizedPnl(details.unrealizedPnl())
		.totalPnl(details.totalPnl())
		.tradeCount(details.tradeCount())
		.experimentalNetRealizedPnl(details.experimentalNetRealizedPnl())
		.experimentalUnrealizedPnl(details.experimentalUnrealizedPnl())
		.experimentalTotalPnl(details.experimentalTotalPnl());
		return sbe.encodedLength();		
	}

	public static int encodeSecurityPositionOnly(final MutableDirectBuffer dstBuffer, 
			int offset, 
			final MutableDirectBuffer stringBuffer,
			PositionSbeEncoder sbe,
			SecurityPositionDetails details){
		sbe.wrap(dstBuffer, offset)
		.avgBuyPrice(details.avgBuyPrice())
		.avgSellPrice(details.avgSellPrice())
		.entitySid(details.entitySid())
		.entityType(details.entityType())
		.mtmSellPrice(details.mtmSellPrice())
		.mtmBuyPrice(details.mtmBuyPrice())
		.openPosition(details.openPosition())
		.openCallPosition(details.openCallPosition())
		.openPutPosition(details.openPutPosition())	
		.buyQty(details.buyQty())
		.buyNotional(details.buyNotional())
		.sellQty(details.sellQty())
		.sellNotional(details.sellNotional())
		.capUsed(details.capUsed())
		.maxCapUsed(details.maxCapUsed())
		.osBuyQty(details.osBuyQty())
		.osBuyNotional(details.osBuyNotional())
		.osSellQty(details.osSellQty())
		.osSellNotional(details.osSellNotional())
		.fees(details.fees())
		.commission(details.commission())
		.netRealizedPnl(details.netRealizedPnl())
		.unrealizedPnl(details.unrealizedPnl())
		.totalPnl(details.totalPnl())
		.tradeCount(details.tradeCount())
		.experimentalNetRealizedPnl(details.experimentalNetRealizedPnl())
		.experimentalUnrealizedPnl(details.experimentalUnrealizedPnl())
		.experimentalTotalPnl(details.experimentalTotalPnl());
		return sbe.encodedLength();		
	}
}
