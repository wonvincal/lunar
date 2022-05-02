package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.service.ServiceConstant;

public class TradeFbsEncoder {
	static final Logger LOG = LogManager.getLogger(TradeFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, TradeSbeDecoder trade){
		int limit = trade.limit();
		
		stringBuffer.clear();
		stringBuffer.limit(trade.getExecutionId(stringBuffer.array(), 0));
		int executionIdOffset = builder.createString(stringBuffer);
		
	    TradeFbs.startTradeFbs(builder);
	    TradeFbs.addChannelId(builder, trade.channelId());
	    TradeFbs.addChannelSnapshotSeq(builder, trade.channelSnapshotSeq());
	    if (trade.createTime() != ServiceConstant.NULL_TIME_NS){
	    	TradeFbs.addCreateTime(builder, trade.createTime());
	    }
	    if (trade.updateTime() != ServiceConstant.NULL_TIME_NS){
	    	TradeFbs.addUpdateTime(builder, trade.updateTime());
	    }
	    TradeFbs.addTradeSid(builder, trade.tradeSid());
	    TradeFbs.addOrderSid(builder, trade.orderSid());
	    TradeFbs.addSecSid(builder, trade.secSid());
	    TradeFbs.addSide(builder, trade.side().value());
	    TradeFbs.addOrderId(builder, trade.orderId());
	    TradeFbs.addStatus(builder, trade.status().value());
	    TradeFbs.addTradeStatus(builder, trade.tradeStatus().value());
	    TradeFbs.addCumulativeQty(builder, trade.cumulativeQty());
	    TradeFbs.addLeavesQty(builder, trade.leavesQty());
	    TradeFbs.addExecutionId(builder, executionIdOffset);
	    TradeFbs.addExecutionQty(builder, trade.executionQty());
	    TradeFbs.addExecutionPrice(builder, trade.executionPrice());
	    trade.limit(limit);
	    return TradeFbs.endTradeFbs(builder);
	}
}
