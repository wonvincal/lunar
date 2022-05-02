package com.lunar.order;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.common.base.Strings;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeEncoder;
import com.lunar.message.io.sbe.TradeStatus;

public class Trade {
	static final Logger LOG = LogManager.getLogger(Trade.class);
	
	private long secSid;
	private final int sid;
	private final int leavesQty;
	private final int cumulativeQty;
	private final String executionId;
	private final int executionPrice;
	private final int executionQty;
	private final int orderSid;
	private final int orderId;
	private final Side side;
	private final OrderStatus orderStatus;
	private final TradeStatus tradeStatus;
	private int channelId;
	private long channelSeq;
	private long createTime;
	private long updateTime;
	
	public static Trade of(TradeSbeDecoder sbe, MutableDirectBuffer buffer){
		
		return new Trade(sbe.tradeSid(), sbe.orderSid(), sbe.orderId(), sbe.secSid(), sbe.side(), 
				sbe.leavesQty(), sbe.cumulativeQty(),
				new String(buffer.byteArray(), 0, sbe.getExecutionId(buffer.byteArray(), 0)),
				sbe.executionPrice(), sbe.executionQty(), 
				sbe.status(), sbe.tradeStatus(),
				sbe.createTime(), sbe.updateTime());
	}
	
	public static Trade of(int sid, int orderSid, int orderId, long secSid, Side side,
			int leavesQty, int cumulativeQty,
			String executionId, int executionPrice, int executionQty,
			OrderStatus orderStatus,
			TradeStatus tradeStatus, 
			long createTime, long updateTime){
		return new Trade(sid, orderSid, orderId, secSid, side, leavesQty, cumulativeQty, executionId, executionPrice, executionQty, orderStatus, tradeStatus, createTime, updateTime);
	}
	
	Trade(int sid, int orderSid, int orderId, long secSid, Side side,
			int leavesQty, 
			int cumulativeQty,
			String executionId,
			int executionPrice, 
			int executionQty, 
			OrderStatus orderStatus,
			TradeStatus tradeStatus,
			long createTime,
			long updateTime){
		this.secSid = secSid;
		this.sid = sid;
		this.orderSid = orderSid;
		this.orderId = orderId;
		this.side = side;
		this.leavesQty = leavesQty;
		this.cumulativeQty = cumulativeQty;
		
		if (executionId.length() < TradeSbeEncoder.executionIdLength()){
			LOG.error("Execution ID must have size of {}, pad with space now. [executionId:{}]", TradeSbeEncoder.executionIdLength(), executionId);
			executionId = Strings.padEnd(executionId, TradeSbeEncoder.executionIdLength(), ' ');
		}
		else if (executionId.length() > TradeSbeEncoder.executionIdLength()){
			LOG.error("Execution ID exceeds max size of {}, truncate. [executionId:{}]", TradeSbeEncoder.executionIdLength(), executionId);
			executionId = executionId.substring(0, TradeSbeEncoder.executionIdLength());
		}
		
		this.executionId = executionId;
		this.executionPrice = executionPrice;
		this.executionQty = executionQty;
		this.orderStatus = orderStatus;
		this.tradeStatus = tradeStatus;
		this.createTime = createTime;
		this.updateTime = updateTime;
	}
	
	public int sid(){
		return sid;
	}
	
	public int orderId(){
		return orderId;
	}
	
	public String executionId(){
		return executionId;
	}

	public int orderSid(){
		return orderSid;
	}
	
	public Side side(){
		return side;
	}

	public int leavesQty(){
		return leavesQty;
	}

	public int cumulativeQty(){
		return cumulativeQty;
	}

	public int executionPrice(){
		return executionPrice;
	}

	public int executionQty(){
		return executionQty;
	}
	
	public TradeStatus tradeStatus(){
		return tradeStatus;
	}
	
	public long createTime(){
		return createTime;
	}
	
	public long updateTime(){
		return updateTime;
	}

	public int channelId(){
		return this.channelId;
	}
	
	public long channelSeq(){
		return this.channelSeq;
	}
	
	public Trade channelId(int value){
		this.channelId = value;
		return this;
	}
	
	public Trade channelSeq(long value){
		this.channelSeq = value;
		return this;
	}

	public long secSid(){
		return this.secSid;
	}
	
	public OrderStatus orderStatus(){
		return this.orderStatus;
	}
}