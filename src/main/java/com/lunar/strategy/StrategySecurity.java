package com.lunar.strategy;

import java.util.Optional;

import com.lunar.entity.Issuer;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.pricing.Greeks;
import com.lunar.service.ServiceConstant;

public class StrategySecurity extends Security {
    private MarketOrderBook orderBook;
    private MarketTrade lastMarketTrade;
    private Greeks greeks;
    private long position;
    private long pendingSell;
    private long channelSeq = ServiceConstant.START_CHANNEL_SEQ - 1;
    private final int assignedThrottleTrackerIndex;
    
    private int limitOrderSid;
    private int limitOrderPrice;
    private long limitOrderQuantity;
    
    private long pricingSecSid = INVALID_SECURITY_SID;
    private StrategySecurity underlying;
    private StrategySecurity pricingInstrument;
    private Issuer issuer;
    
    private MarketDataUpdateHandler marketDataUpdateHandler;
    private GreeksUpdateHandler greeksUpdateHandler;
    private OrderStatusReceivedHandler orderStatusReceivedHandler;
    
    private Strategy activeStrategy;
    private int sortOrder;
    
	public static StrategySecurity of(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, boolean algo, SpreadTable spreadTable, MarketOrderBook orderBook, int assignedThrottleTrackerIndex) {
		return new StrategySecurity(sid, secType, code, exchangeSid, undSecSid, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, spreadTable, orderBook, 0, assignedThrottleTrackerIndex);
	}

	public static StrategySecurity of(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, boolean algo, SpreadTable spreadTable, MarketOrderBook orderBook) {
		return new StrategySecurity(sid, secType, code, exchangeSid, undSecSid, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, spreadTable, orderBook, 0, 0);
	}
	
	public static StrategySecurity of(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, boolean algo, SpreadTable spreadTable, MarketOrderBook orderBook, long initialPosition) {
		return new StrategySecurity(sid, secType, code, exchangeSid, undSecSid, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, spreadTable, orderBook, initialPosition, 0);
	}

	public StrategySecurity(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid,
			PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, boolean algo, SpreadTable spreadTable, 
			MarketOrderBook orderBook) {
		this(sid, secType, code, exchangeSid, undSecSid, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, spreadTable, orderBook, 0, 0);
	}

	public StrategySecurity(long sid, SecurityType secType, String code, int exchangeSid, long undSecSid,
			PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize, boolean algo, SpreadTable spreadTable,
			MarketOrderBook orderBook, long initialPosition, int assignedThrottleTrackerIndex) {
		super(sid, secType, code, exchangeSid, undSecSid, Optional.empty(), ServiceConstant.NULL_LISTED_DATE, putOrCall, optionStyle, strikePrice, convRatio, issuerSid, lotSize, algo, spreadTable);
		this.orderBook = orderBook;
		this.lastMarketTrade = MarketTrade.of(orderBook.triggerInfo()).secSid(sid);
		this.greeks = Greeks.of(sid);
		this.position = initialPosition;
		this.assignedThrottleTrackerIndex = assignedThrottleTrackerIndex;
		this.pricingSecSid = undSecSid;
	}

    public long channelSeq() {
        return channelSeq;
    }
    public StrategySecurity channelSeq(final long channelSeq) {
        this.channelSeq = channelSeq;
        return this;
    }
    
    public MarketOrderBook orderBook(){
        return orderBook;
    }
    
    public MarketTrade lastMarketTrade() {
        return lastMarketTrade;
    }
    
    public long position() {
        return position;
    }
    
    public long updatePosition(final long quantity) {
        position += quantity;
        return position;
    }
    
    public StrategySecurity position(final long position) {
        this.position = position;
        return this;
    }
    
    public long availablePosition() {
        return position - pendingSell;
    }
    
    public long pendingSell() {
        return pendingSell;
    }
    
    public int limitOrderSid() {
        return limitOrderSid;
    }
    public StrategySecurity limitOrderSid(final int limitOrderSid) {
        this.limitOrderSid = limitOrderSid;
        return this;
    }
    
    public int limitOrderPrice() {
        return limitOrderPrice;
    }
    public StrategySecurity limitOrderPrice(final int limitOrderPrice) {
        this.limitOrderPrice = limitOrderPrice;
        return this;
    }

    public long limitOrderQuantity() {
        return limitOrderQuantity;
    }
    public StrategySecurity limitOrderQuantity(final long limitOrderQuantity) {
        this.limitOrderQuantity = limitOrderQuantity;
        return this;
    }
    
    public int assignedThrottleTrackerIndex() {
        return assignedThrottleTrackerIndex;
    }
    
    public long updatePendingSell(final long quantity) {
        pendingSell += quantity;
        return pendingSell;
    }
    
    public StrategySecurity pendingSell(final long pendingSell) {
        this.pendingSell = pendingSell;
        return this;
    }
    
    public MarketDataUpdateHandler marketDataUpdateHandler() {
        return this.marketDataUpdateHandler;
    }
    
    public GreeksUpdateHandler greeksUpdateHandler() {
        return this.greeksUpdateHandler;
    }
    
    public OrderStatusReceivedHandler orderStatusReceivedHandler() {
        return this.orderStatusReceivedHandler;
    }
    
    public void registerMdUpdateHandler(final MarketDataUpdateHandler mdUpdateHandler) {
        if (this.marketDataUpdateHandler == null) {
            this.marketDataUpdateHandler = mdUpdateHandler;
        }
        else if (this.marketDataUpdateHandler != mdUpdateHandler) {
            final MultiCompositeMarketDataUpdateHandler multipleHandler;
            if (this.marketDataUpdateHandler instanceof MultiCompositeMarketDataUpdateHandler) {
                multipleHandler = (MultiCompositeMarketDataUpdateHandler)this.marketDataUpdateHandler;               
            }
            else {
                multipleHandler = new MultiCompositeMarketDataUpdateHandler(this);
                multipleHandler.registerOrderBookUpdateHandler(this.marketDataUpdateHandler);
                this.marketDataUpdateHandler = multipleHandler;
            }
            multipleHandler.registerOrderBookUpdateHandler(mdUpdateHandler);
        }
    }
    
    public void unregisterMdUpdateHandler(final MarketDataUpdateHandler handler) {
        if (this.marketDataUpdateHandler == handler) {
            this.marketDataUpdateHandler = null;
        }
        else if (this.marketDataUpdateHandler instanceof MultiCompositeMarketDataUpdateHandler) {
            final MultiCompositeMarketDataUpdateHandler multipleHandler = (MultiCompositeMarketDataUpdateHandler)this.marketDataUpdateHandler;
            multipleHandler.unregisterOrderBookUpdateHandler(handler);
        }
    }
    
    public void registerGreeksUpdateHandler(final GreeksUpdateHandler handler) {
        if (this.greeksUpdateHandler == null) {
            this.greeksUpdateHandler = handler;
        }
        else if (this.greeksUpdateHandler != handler) {
            final MultiCompositeGreeksUpdateHandler multipleHandler;
            if (this.greeksUpdateHandler instanceof MultiCompositeGreeksUpdateHandler) {
                multipleHandler = (MultiCompositeGreeksUpdateHandler)this.greeksUpdateHandler;               
            }
            else {
                multipleHandler = new MultiCompositeGreeksUpdateHandler();
                multipleHandler.registerGreeksUpdateHandler(this.greeksUpdateHandler);
                this.greeksUpdateHandler = multipleHandler;
            }
            multipleHandler.registerGreeksUpdateHandler(handler);
        }
    }
    
    public void unregisterGreeksUpdateHandler(final GreeksUpdateHandler handler) {
        if (this.greeksUpdateHandler == handler) {
            this.greeksUpdateHandler = null;
        }
        else if (this.greeksUpdateHandler instanceof MultiCompositeGreeksUpdateHandler) {
            final MultiCompositeGreeksUpdateHandler multipleHandler = (MultiCompositeGreeksUpdateHandler)this.greeksUpdateHandler;
            multipleHandler.unregisterGreeksUpdateHandler(handler);
        }
    }
    
    public void registerOrderStatusReceivedHandler(final OrderStatusReceivedHandler handler) {
        if (this.orderStatusReceivedHandler == null) {
            this.orderStatusReceivedHandler = handler;
        }
        else if (this.orderStatusReceivedHandler != handler) {
            final MultiCompositeOrderStatusReceivedHandler multipleHandler;
            if (this.orderStatusReceivedHandler instanceof MultiCompositeOrderStatusReceivedHandler) {
                multipleHandler = (MultiCompositeOrderStatusReceivedHandler)this.orderStatusReceivedHandler;               
            }
            else {
                multipleHandler = new MultiCompositeOrderStatusReceivedHandler();
                multipleHandler.registerOrderStatusReceivedHandler(this.orderStatusReceivedHandler);
                this.orderStatusReceivedHandler = multipleHandler;
            }
            multipleHandler.registerOrderStatusReceivedHandler(handler);
        }
    }
    
    public void unregisterOrderStatusReceivedHandler(final OrderStatusReceivedHandler handler) {
        if (this.orderStatusReceivedHandler == handler) {
            this.orderStatusReceivedHandler = null;
        }
        else if (this.orderStatusReceivedHandler instanceof MultiCompositeOrderStatusReceivedHandler) {
            final MultiCompositeOrderStatusReceivedHandler multipleHandler = (MultiCompositeOrderStatusReceivedHandler)this.orderStatusReceivedHandler;
            multipleHandler.unregisterOrderStatusReceivedHandler(handler);
        }
    }
    
    public Strategy activeStrategy() {
        return this.activeStrategy;
    }
    
    public StrategySecurity activeStrategy(final Strategy strategy) {
        this.activeStrategy = strategy;
        return this;
    }
    
    public int sortOrder() {
        return this.sortOrder;
    }
    public StrategySecurity sortOrder(final int sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }
    
    public long pricingSecSid() {
        return this.pricingSecSid;
    }
    public StrategySecurity pricingSecSid(final long pricingSecSid) {
        this.pricingSecSid = pricingSecSid;
        return this;
    }
    
    public StrategySecurity underlying() {
        return this.underlying;
    }
    public StrategySecurity underlying(final StrategySecurity underlying) {
        this.underlying = underlying;
        return this;
    }
    
    public StrategySecurity pricingInstrument() {
        return this.pricingInstrument;
    }
    public StrategySecurity pricingInstrument(final StrategySecurity pricingInstrument) {
        this.pricingInstrument = pricingInstrument;
        return this;
    }
    
    public Issuer issuer() {
        return this.issuer;
    }
    public StrategySecurity issuer(final Issuer issuer) {
        this.issuer = issuer;
        return this;
    }
    
    public Greeks greeks() {
        return this.greeks;
    }
}
