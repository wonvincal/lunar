package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.io.sbe.PositionSbeDecoder;

public class PositionFbsEncoder {
	static final Logger LOG = LogManager.getLogger(PositionFbsEncoder.class);
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, PositionSbeDecoder position){
		int limit = position.limit();
	    PositionFbs.startPositionFbs(builder);
	    PositionFbs.addEntitySid(builder, position.entitySid());
	    PositionFbs.addEntityType(builder, position.entityType().value());
	    if (position.avgBuyPrice() != PositionSbeDecoder.avgBuyPriceNullValue()){
	    	PositionFbs.addAvgBuyPrice(builder, position.avgBuyPrice());
	    }
	    if (position.avgSellPrice() != PositionSbeDecoder.avgSellPriceNullValue()){
	    	PositionFbs.addAvgSellPrice(builder, position.avgSellPrice());
	    }
	    if (position.commission() != PositionSbeDecoder.commissionNullValue()){
	    	PositionFbs.addCommission(builder, position.commission());
	    }
	    if (position.fees() != PositionSbeDecoder.feesNullValue()){
	    	PositionFbs.addFees(builder, position.fees());
	    }
	    if (position.mtmSellPrice() != PositionSbeDecoder.mtmSellPriceNullValue()){
	    	PositionFbs.addMtmSellPrice(builder, position.mtmSellPrice());
	    }
	    if (position.mtmBuyPrice() != PositionSbeDecoder.mtmBuyPriceNullValue()){
	    	PositionFbs.addMtmBuyPrice(builder, position.mtmBuyPrice());
	    }
	    if (position.netRealizedPnl() != PositionSbeDecoder.netRealizedPnlNullValue()){
	    	PositionFbs.addNetRealizedPnl(builder, position.netRealizedPnl());
	    }
	    if (position.openPosition() != PositionSbeDecoder.openPositionNullValue()){
	    	PositionFbs.addOpenPosition(builder, position.openPosition());
	    }
	    if (position.openCallPosition() != PositionSbeDecoder.openCallPositionNullValue()){
	    	PositionFbs.addOpenCallPosition(builder, position.openCallPosition());
	    }
	    if (position.openPutPosition() != PositionSbeDecoder.openPutPositionNullValue()){
	    	PositionFbs.addOpenPutPosition(builder, position.openPutPosition());
	    }
	    if (position.osBuyNotional() != PositionSbeDecoder.osBuyNotionalNullValue()){
	    	PositionFbs.addOsBuyNotional(builder, position.osBuyNotional());
	    }
	    if (position.buyQty() != PositionSbeDecoder.buyQtyNullValue()){
	    	PositionFbs.addBuyQty(builder, position.buyQty());
	    }
	    if (position.sellQty() != PositionSbeDecoder.sellQtyNullValue()){
	    	PositionFbs.addSellQty(builder, position.sellQty());
	    }
	    if (position.osBuyQty() != PositionSbeDecoder.osBuyQtyNullValue()){
	    	PositionFbs.addOsBuyQty(builder, position.osBuyQty());
	    }
	    if (position.osSellNotional() != PositionSbeDecoder.osSellNotionalNullValue()){
	    	PositionFbs.addOsSellNotional(builder, position.osSellNotional());
	    }
	    if (position.osSellQty() != PositionSbeDecoder.osSellQtyNullValue()){
	    	PositionFbs.addOsSellQty(builder, position.osSellQty());
	    }
	    if (position.totalPnl() != PositionSbeDecoder.totalPnlNullValue()){
	    	PositionFbs.addTotalPnl(builder, position.totalPnl());
	    }
	    if (position.unrealizedPnl() != PositionSbeDecoder.unrealizedPnlNullValue()){
	    	PositionFbs.addUnrealizedPnl(builder, position.unrealizedPnl());
	    }
	    if (position.tradeCount() != PositionSbeDecoder.tradeCountNullValue()){
	    	PositionFbs.addTradeCount(builder, position.tradeCount());
	    }
	    if (position.buyNotional() != PositionSbeDecoder.buyNotionalNullValue()){
	    	PositionFbs.addBuyNotional(builder, position.buyNotional());
	    }
	    if (position.sellNotional() != PositionSbeDecoder.sellNotionalNullValue()){
	    	PositionFbs.addSellNotional(builder, position.sellNotional());
	    }
	    if (position.capUsed() != PositionSbeDecoder.capUsedNullValue()){
	    	PositionFbs.addCapUsed(builder, position.capUsed());
	    }
	    if (position.maxCapUsed() != PositionSbeDecoder.maxCapUsedNullValue()){
	    	PositionFbs.addMaxCapUsed(builder, position.maxCapUsed());
	    }
	    if (position.experimentalUnrealizedPnl() != PositionSbeDecoder.experimentalUnrealizedPnlNullValue()){
	    	PositionFbs.addExperimentalUnrealizedPnl(builder, position.experimentalUnrealizedPnl());
	    }
	    if (position.experimentalNetRealizedPnl() != PositionSbeDecoder.experimentalNetRealizedPnlNullValue()){
	    	PositionFbs.addExperimentalNetRealizedPnl(builder, position.experimentalNetRealizedPnl());
	    }
	    if (position.experimentalTotalPnl() != PositionSbeDecoder.experimentalTotalPnlNullValue()){
	    	PositionFbs.addExperimentalTotalPnl(builder, position.experimentalTotalPnl());
	    }
	    position.limit(limit);
	    return PositionFbs.endPositionFbs(builder);
	}

}
