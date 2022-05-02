package com.lunar.position;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;

public class PositionTracker {	
	
	public interface Listener {
		public void update(Position position);		
	}	
	
	public static class Position {
		private final long secSid;
		private double netTradedNotional;
		private double outstandingBuyNotional;
		private double outstandingSellNotional;
				
		public Position(long secSid, double netTradedNotional, double outstandingBuyNotional, double outstandingSellNotional) {
			this.secSid = secSid;
			this.netTradedNotional = netTradedNotional;
			this.outstandingBuyNotional = outstandingBuyNotional;
			this.outstandingSellNotional = outstandingSellNotional;
		}
				
		public Position(long secSid) {
			this(secSid, 0, 0, 0);
		}
		
		public static Position clone(Position p) {
			return new Position(p.getSecSid(), p.getNetTradedNotional(), p.getOutstandingBuyNotional(), p.getOutstandingSellNotional());
		}
		
		public long getSecSid() {
			return this.secSid;
		}

		public double getNetTradedNotional() {
			return netTradedNotional;
		}

		public double getOutstandingBuyNotional() {
			return outstandingBuyNotional;
		}

		public double getOutstandingSellNotional() {
			return outstandingSellNotional;
		}
	}
	
	private final List<Listener> listeners = new ArrayList<Listener>();
	private final Int2DoubleOpenHashMap notionalByTradeSid = new Int2DoubleOpenHashMap();	
	private final Int2DoubleOpenHashMap notionalByOrderSid = new Int2DoubleOpenHashMap();
	private final Position position;
	
	public PositionTracker(Long secSid) {	
		this.position = new Position(secSid);
	}
	
	public void addListener(Listener listener) {
		listeners.add(listener);
	}
	
	private void publishToListeners() {
		for (Listener l : listeners) {
			l.update(Position.clone(position));
		}					
	}
		
	public void handleTrade(int tradeSid, double price, double quantity) {
		// We are keeping the notional per trade sid to support trade cancellation.
		// This also prevents us from double counting a trade if it's received 
		// more than once.
		if (notionalByTradeSid.containsKey(tradeSid)) {					
			position.netTradedNotional -= notionalByTradeSid.get(tradeSid);;
		}		
		double notional = price * quantity;
		notionalByTradeSid.put(tradeSid, notional);
		position.netTradedNotional += notional;				
		
		publishToListeners();
	}
	
	public void handleOrder(int orderSid, double price, double quantity) {
		if (notionalByOrderSid.containsKey(orderSid)) {
			double oldNotional = notionalByOrderSid.get(orderSid);
			if (oldNotional > 0) 
				position.outstandingBuyNotional -= oldNotional;
			else
				position.outstandingSellNotional -= (-oldNotional);								
		}		
		
		double notional = price * quantity; 
		notionalByOrderSid.put(orderSid, notional);
		if (quantity > 0)
			position.outstandingBuyNotional += notional;
		else
			position.outstandingSellNotional += (-notional);
		
		publishToListeners();
	}
	
	public Position getPosition() {
		return Position.clone(position);
	}
}
