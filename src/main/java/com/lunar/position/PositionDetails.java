 package com.lunar.position;

import org.agrona.MutableDirectBuffer;

import com.lunar.core.SbeEncodable;
import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.EntityType;
import com.lunar.message.io.sbe.PositionSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.PositionSender;

public class PositionDetails implements SbeEncodable {
	private static final long NA_SID = -1; 
	private static final EntityType NA_ENTITY_TYPE = EntityType.NULL_VAL; 
	private final long entitySid;
	private final EntityType entityType;
	protected double netRealizedPnl;
	protected double unrealizedPnl;
	protected double fees;
	protected double commission;
	protected int tradeCount;
	
	/**
	 * Requested - make openPosition available in PositionDetails instead of
	 * SecurityPositionDetails.
	 */
	protected long openPosition;
	protected long openCallPosition;
	protected long openPutPosition;
	protected long buyQty;
	protected long sellQty;
	protected double buyNotional;
	protected double sellNotional;
	
	protected long osBuyQty;
	protected long osSellQty;
	protected double osBuyNotional;
	protected double osSellNotional;
	protected double capUsed;
	protected double maxCapUsed;

	protected double experimentalNetRealizedPnl;
	protected double experimentalUnrealizedPnl;

	public static PositionDetails of(long entitySid, EntityType entityType){
		return new PositionDetails(entitySid, entityType, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static PositionDetails of(){
		return new PositionDetails(NA_SID, NA_ENTITY_TYPE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static PositionDetails cloneOf(PositionDetails details){
		return new PositionDetails(details.entitySid, 
				details.entityType,
				details.fees, 
				details.commission, 
				details.netRealizedPnl, 
				details.unrealizedPnl,
				details.tradeCount,
				details.openPosition,
				details.openCallPosition,
				details.openPutPosition,
				details.buyQty,
				details.buyNotional,
				details.sellQty,
				details.sellNotional,
				details.osBuyQty,
				details.osBuyNotional,
				details.osSellQty,
				details.osSellNotional,
				details.capUsed,
				details.maxCapUsed,
				details.experimentalNetRealizedPnl,
				details.experimentalUnrealizedPnl);
	}
	
	PositionDetails(long entitySid,
			EntityType entityType,
			double fees,
			double commission,
			double netRealizedPnl,
			double unrealizedPnl,
			int tradeCount,
			long openPosition,
			long openCallPosition,
			long openPutPosition,
			long buyQty,
			double buyNotional,
			long sellQty,
			double sellNotional,
			long osBuyQty,
			double osBuyNotional,
			long osSellQty,
			double osSellNotional,
			double capUsed,
			double maxCapUsed,
			double experimentalNetRealizedPnl,
			double experimentalUnrealizedPnl){
		this.entitySid = entitySid;
		this.entityType = entityType;
		this.unrealizedPnl = unrealizedPnl;
		this.netRealizedPnl = netRealizedPnl;
		this.fees = fees;
		this.commission = commission;
		this.tradeCount = tradeCount;
		this.openPosition = openPosition;
		this.openCallPosition = openCallPosition;
		this.openPutPosition = openPutPosition;
		this.buyQty = buyQty;
		this.sellQty = sellQty;
		this.buyNotional = buyNotional;
		this.sellNotional = sellNotional;
		this.osBuyQty = osBuyQty;
		this.osSellQty = osSellQty;
		this.osBuyNotional = osBuyNotional;
		this.osSellNotional = osSellNotional;
		this.capUsed = capUsed;
		this.maxCapUsed = maxCapUsed;
		this.experimentalNetRealizedPnl = experimentalNetRealizedPnl;
		this.experimentalUnrealizedPnl = experimentalUnrealizedPnl;
	}
	
	public long entitySid() {
		return entitySid;
	}
	
	public EntityType entityType() {
		return entityType;
	}	

	public int tradeCount() {
		return tradeCount;
	}

	public PositionDetails tradeCount(int tradeCount) {
		this.tradeCount = tradeCount;
		return this;
	}

	public double buyNotional() {
		return this.buyNotional;
	}

	public PositionDetails buyNotional(double buyNotional) {
		this.buyNotional = buyNotional;
		return this;
	}

	public double sellNotional() {
		return this.sellNotional;
	}
	
	public PositionDetails sellNotional(double sellNotional) {
		this.sellNotional = sellNotional;
		return this;
	}

	public long osBuyQty() {
		return osBuyQty;
	}

	public PositionDetails osBuyQty(long osBuyQty) {
		this.osBuyQty = osBuyQty;
		return this;
	}

	public double osBuyNotional() {
		return osBuyNotional;
	}

	public PositionDetails osBuyNotional(double osBuyNotional) {
		this.osBuyNotional = osBuyNotional;
		return this;
	}

	public long osSellQty() {
		return osSellQty;
	}

	public PositionDetails osSellQty(long osSellQty) {
		this.osSellQty = osSellQty;
		return this;
	}

	public double osSellNotional() {
		return osSellNotional;
	}

	public PositionDetails osSellNotional(double osSellNotional) {
		this.osSellNotional = osSellNotional;
		return this;
	}

	public double capUsed() {
		return capUsed;
	}

	public PositionDetails capUsed(double capUsed) {
		this.capUsed = capUsed;
		maxCapUsed(Math.max(this.capUsed, this.maxCapUsed));
		return this;
	}

	public double maxCapUsed() {
		return maxCapUsed;
	}

	public PositionDetails maxCapUsed(double maxCapUsed) {
		this.maxCapUsed = maxCapUsed;
		return this;
	}
	
	public PositionDetails fees(double value) {
		this.fees = value;
		return this;
	}

	public double commission() {
		return this.commission;
	}

	public PositionDetails commission(double value) {
		this.commission = value;
		return this;
	}

	public double totalPnl() {
		return netRealizedPnl() + unrealizedPnl();
	}

	public double experimentalTotalPnl() {
		return experimentalNetRealizedPnl() + experimentalUnrealizedPnl();
	}

	public double netRealizedPnl() {
		return netRealizedPnl;
	}
	
	public PositionDetails netRealizedPnl(double netRealizedPnl) {
		this.netRealizedPnl = netRealizedPnl;
		return this;
	}
	
	public double unrealizedPnl() {
		return unrealizedPnl;
	}

	public PositionDetails unrealizedPnl(double unrealizedPnl) {
		this.unrealizedPnl = unrealizedPnl;
		return this;
	}

	public double experimentalNetRealizedPnl() {
		return experimentalNetRealizedPnl;
	}
	
	public PositionDetails experimentalNetRealizedPnl(double experimentalNetRealizedPnl) {
		this.experimentalNetRealizedPnl = experimentalNetRealizedPnl;
		return this;
	}
	
	public double experimentalUnrealizedPnl() {
		return experimentalUnrealizedPnl;
	}

	public PositionDetails experimentalUnrealizedPnl(double experimentalUnrealizedPnl) {
		this.experimentalUnrealizedPnl = experimentalUnrealizedPnl;
		return this;
	}

	public long buyQty() {
		return buyQty;
	}

	public PositionDetails buyQty(long buyQty) {
		this.buyQty = buyQty;
		return this;
	}

	public long sellQty() {
		return sellQty;
	}

	public double fees() {
		return this.fees;
	}
	
	public PositionDetails sellQty(long sellQty) {
		this.sellQty = sellQty;
		return this;
	}

	public long openPosition() {
		return openPosition;
	}

	public PositionDetails openPosition(long openPosition) {
		this.openPosition = openPosition;
		return this;
	}

	public long openCallPosition() {
		return openCallPosition;
	}

	public PositionDetails openCallPosition(long openCallPosition) {
		this.openCallPosition = openCallPosition;
		return this;
	}
	
	public long openPutPosition() {
		return openPutPosition;
	}

	public PositionDetails openPutPosition(long openPutPosition) {
		this.openPutPosition = openPutPosition;
		return this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (entitySid ^ (entitySid >>> 32));
		result = prime * result + ((entityType == null) ? 0 : entityType.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PositionDetails other = (PositionDetails) obj;
		if (entitySid != other.entitySid)
			return false;
		if (entityType != other.entityType)
			return false;
		return true;
	}

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return PositionSender.encodePositionOnly(buffer, 
				offset, 
				stringBuffer, 
				encoder.positionSbeEncoder(),
				this);
	}

	@Override
	public TemplateType templateType() {
		return TemplateType.POSITION;
	}
	
	@Override
	public short blockLength() {
		return PositionSbeEncoder.BLOCK_LENGTH;
	}
	
	@Override
	public int expectedEncodedLength() {
		return PositionSbeEncoder.BLOCK_LENGTH;
	}
	
    @Override
    public int schemaId() {
        return PositionSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return PositionSbeEncoder.SCHEMA_VERSION;
    }
    
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("entitySid:").append(entitySid)
		.append(", entityType:").append(entityType.name())
		.append(", netRealizedPnl:").append(netRealizedPnl)
		.append(", unrealizedPnl:").append(unrealizedPnl).append(", fees:").append(fees)
		.append(", commission:").append(commission)
		.append(", tradeCount:").append(tradeCount)
		.append(", openPosition:").append(openPosition)
		.append(", openCallPosition:").append(openCallPosition)
		.append(", openPutPosition:").append(openPutPosition)
		.append(", buyQty:").append(buyQty)
		.append(", sellQty:").append(sellQty)
		.append(", buyNotional:").append(buyNotional)
		.append(", sellNotional:").append(sellNotional)
		.append(", osBuyQty:").append(osBuyQty)
		.append(", osSellQty:").append(osSellQty)
		.append(", osBuyNotional:").append(osBuyNotional)
		.append(", osSellNotional:").append(osSellNotional)
		.append(", capUsed:").append(capUsed)
		.append(", maxCapUsed:").append(maxCapUsed)
		.append(", experimentalNetRealizedPnl:").append(experimentalNetRealizedPnl)
		.append(", experimentalUnrealizedPnl:").append(experimentalUnrealizedPnl);
		return builder.toString();
	}

}
