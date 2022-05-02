package com.lunar.position;

import com.lunar.position.PositionTracker.Listener;
import com.lunar.position.PositionTracker.Position;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class UnderlyingPosition implements Listener {	
	private final long underlyingSid;
	private final Long2ObjectOpenHashMap<Position> positionBySecuritySid = new Long2ObjectOpenHashMap<>();
	private double netTradedNotional = 0;
	private double outstandingBuyNotional = 0;
	private double outstandingSellNotional = 0;
		
	public UnderlyingPosition(long underlyingSid) {
		this.underlyingSid = underlyingSid;
	}
	
	@Override
	public void update(Position position) {		
		Position oldPosition = positionBySecuritySid.get(position.getSecSid());
		
		netTradedNotional -= oldPosition.getNetTradedNotional();
		outstandingBuyNotional -= oldPosition.getOutstandingBuyNotional();
		outstandingSellNotional -= oldPosition.getOutstandingSellNotional();
		
		positionBySecuritySid.put(position.getSecSid(), position);
		netTradedNotional += position.getNetTradedNotional();
		outstandingBuyNotional += position.getOutstandingBuyNotional();
		outstandingSellNotional += position.getOutstandingSellNotional();				
	}
	
	public void newPositionTracker(PositionTracker positionTracker) {
		Position position = positionTracker.getPosition();
		
		if (positionBySecuritySid.containsKey(position.getSecSid())) {
			throw new RuntimeException(String.format("Secutiy sid: %d has already been added previously", position.getSecSid()));
		}
		
		// TODO: Early return if the underlying sid of the position passed in does not match this.underlyingSid
				
		positionBySecuritySid.put(position.getSecSid(), position);
		netTradedNotional += position.getNetTradedNotional();
		outstandingBuyNotional += position.getOutstandingBuyNotional();
		outstandingSellNotional += position.getOutstandingSellNotional();
		
		positionTracker.addListener(this);
	}

	public long getUnderlyingSid() {
		return underlyingSid;
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
