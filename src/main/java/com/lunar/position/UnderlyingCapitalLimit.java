package com.lunar.position;

import com.lunar.message.io.sbe.Side;
import com.lunar.order.Order;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class UnderlyingCapitalLimit {

	private Long2IntOpenHashMap underlyingLimits = new Long2IntOpenHashMap();
	private Long2ObjectOpenHashMap<UnderlyingPosition> underlyingPositions = new Long2ObjectOpenHashMap<>();	
	
	public UnderlyingCapitalLimit() {
		underlyingLimits.defaultReturnValue(-1);
	}
	
	public void setUnderlyingLimit(long secSid, int limit) {
		underlyingLimits.put(secSid, limit);		
	}
	
	public void newPositionTracker(PositionTracker positionTracker) {				
		long undSecSid = 0;  // TODO: get the underlying sid of the position from reference data
		
		UnderlyingPosition undPosition;
		if (!underlyingPositions.containsKey(undSecSid)) {
			undPosition = new UnderlyingPosition(undSecSid);
			underlyingPositions.put(undSecSid, undPosition);
		}
		else
		{
			undPosition = underlyingPositions.get(undSecSid);
		}
		
		undPosition.newPositionTracker(positionTracker);				
	}
	
	public boolean validateOrder(Order order) {
		long undSecSid = 0; // TODO: get the underlyig sid of the order
				
		int limit = underlyingLimits.get(undSecSid);
		
		// No limit specified for the underlying
		if (limit == underlyingLimits.defaultReturnValue())		
			return true;
		
		double orderNotional = order.quantity() * order.limitPrice();
		UnderlyingPosition underlyingPosition = underlyingPositions.get(undSecSid);
		
		// No existing position
		if (underlyingPosition == null)
		{			
			if (orderNotional <= limit)
				return true;
			else
				return false;
		}		
		
		double position = underlyingPosition.getNetTradedNotional();
		position += (order.side() == Side.BUY ? underlyingPosition.getOutstandingBuyNotional() : underlyingPosition.getOutstandingSellNotional()); 		
		  
		if (position + orderNotional <= limit)
			return true;
		else
			return false;		
	}
}
