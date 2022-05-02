package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.service.ServiceConstant;

public class PositionDecoder implements Decoder {
	static final Logger LOG = LogManager.getLogger(PositionDecoder.class);
	private final PositionSbeDecoder sbe = new PositionSbeDecoder();
	private final HandlerList<PositionSbeDecoder> handlerList;
	
	PositionDecoder(Handler<PositionSbeDecoder> nullHandler) {
		this.handlerList = new HandlerList<PositionSbeDecoder>(ServiceConstant.DEFAULT_CODEC_HANDLER_LIST_CAPACITY, nullHandler);
	}

	@Override
	public void decode(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		handlerList.handle(buffer, offset, header, sbe);
	}

	@Override
	public void decodeWithEmbedded(DirectBuffer buffer, int offset, MessageHeaderDecoder header, int clientKey, int responseSeq, BooleanType isLast, int embeddedOffset, int embeddedBlockLength, int embeddedTemplateId){
		sbe.wrap(buffer, offset + embeddedOffset, embeddedBlockLength, PositionSbeDecoder.SCHEMA_VERSION);
		handlerList.handleWithEmbedded(buffer, offset, header, clientKey, responseSeq, isLast, sbe);
	}

	@Override
	public String dump(DirectBuffer buffer, int offset, MessageHeaderDecoder header) {
		sbe.wrap(buffer, offset + header.encodedLength(), header.blockLength(), header.version());
		return decodeToString(sbe);
	}

	public static String decodeToString(PositionSbeDecoder sbe){
		return String.format("entitySid:%d, entityType:%s, openPosition:%d, avgBuyPrice:%f, avgSellPrice:%f, mtmAskPrice:%f, mtmBidPrice:%f, osBuyQty:%d, osBuyNotional:%f, osSellQty:%d, osSellNotional:%f, netRealizedPnl:%f, unrealizedPnl:%f, totalPnl:%f, expNetRealizedPnl:%f, expUnrealizedPnl:%f, expTotalPnl:%f, tradeCount:%d",
				sbe.entitySid(),
				sbe.entityType().name(),
				(sbe.openPosition() != PositionSbeDecoder.openPositionNullValue()) ? sbe.openPosition() : 0, 
				sbe.avgBuyPrice(),
				sbe.avgSellPrice(),
				sbe.mtmSellPrice(),
				sbe.mtmBuyPrice(),
				(sbe.osBuyQty() != PositionSbeDecoder.osBuyQtyNullValue()) ? sbe.osBuyQty() : 0,
				(sbe.osBuyNotional() != PositionSbeDecoder.osBuyNotionalNullValue()) ? sbe.osBuyNotional() : 0,
				(sbe.osSellQty() != PositionSbeDecoder.osSellQtyNullValue()) ? sbe.osSellQty() : 0,
				(sbe.osSellNotional() != PositionSbeDecoder.osSellNotionalNullValue()) ? sbe.osSellNotional() : 0,
				sbe.netRealizedPnl(),
				sbe.unrealizedPnl(),
				sbe.totalPnl(),
				sbe.experimentalNetRealizedPnl(),
				sbe.experimentalUnrealizedPnl(),
				sbe.experimentalTotalPnl(),
				(sbe.tradeCount() != PositionSbeDecoder.tradeCountNullValue()) ? sbe.tradeCount() : 0);
	}

	public PositionSbeDecoder sbe(){
		return this.sbe;
	}
	
	public HandlerList<PositionSbeDecoder> handlerList(){
		return this.handlerList;
	}
	
	public static PositionDecoder of(){
		return new PositionDecoder(NULL_HANDLER);
	}

	static final Handler<PositionSbeDecoder> NULL_HANDLER = new Handler<PositionSbeDecoder>() {
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder codec) {
			LOG.warn("message sent to null handler of PositionDecoder [senderSinkId:{}, dstSinkId:{}]", header.senderSinkId(), header.dstSinkId());
		}
	};

}
