package com.lunar.strategy.cbbctest;

import static org.apache.logging.log4j.util.Unbox.box;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableListMultimap;
import com.lunar.entity.LongEntityManager;
import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.message.Parameter;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.Tick;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;

public class CbbcTest implements MarketDataUpdateHandler {
    private static final Logger LOG = LogManager.getLogger(CbbcTest.class);
    
    final private LongEntityManager<? extends StrategySecurity> securities;
    final private StrategyOrderService orderService;
    final private CbbcTestExplain explain;
    final private StrategySecurity futures;
	private StrategySecurity cbbc;		
	private int triggerPrice;
	private int buyPrice;
	private long buyQuantity;
	
	public CbbcTest(final LongEntityManager<? extends StrategySecurity> securities, final StrategySecurity futures, final StrategyOrderService orderService) {		
		this.securities = securities;
	    this.orderService = orderService;
		this.explain = new CbbcTestExplain();
		this.futures = futures;
		this.futures.registerMdUpdateHandler(this);
	}

	@Override
	public void onOrderBookUpdated(Security security, long transactTime, MarketOrderBook orderBook) throws Exception {
		if (cbbc == null || triggerPrice == 0 || buyPrice == 0 || buyQuantity == 0)
			return;
		final Tick bid = futures.orderBook().bestBidOrNullIfEmpty();
		if (bid != null && bid.price() >= triggerPrice) {
            LOG.info("Trigger price hit - send buy order: secCode {}, buyPrice {}, buyQuantity {}, triggerPrice {}, undBid {}, trigger seqNum {}",  cbbc.code(), box(buyPrice),  box(buyQuantity),  box(triggerPrice), box(bid.price()), box(futures.orderBook().triggerInfo().triggerSeqNum()));
			explain.setExplainValues(bid.price());
			orderService.buy(cbbc, buyPrice, buyQuantity, explain);
			triggerPrice = 0;
		}		
	}


	@Override
	public void onTradeReceived(Security security, long timestamp, MarketTrade trade) throws Exception {
		
	}
	
    public void handleRequest(final MessageSinkRef sender, final RequestSbeDecoder request, final ImmutableListMultimap<ParameterType, Parameter> parameters, final Messenger messenger) throws Exception {
        if (request.requestType() == RequestType.UPDATE) {
            LOG.info("Handling request update for cbbc test...");
            final long securitySid = parameters.get(ParameterType.SECURITY_SID).get(0).valueLong();
            cbbc = securities.get(securitySid);
            buyPrice = parameters.get(ParameterType.PRICE).get(0).valueLong().intValue();
            buyQuantity = parameters.get(ParameterType.QUANTITY).get(0).valueLong().intValue();
            triggerPrice = parameters.get(ParameterType.PARAM_VALUE).get(0).valueLong().intValue();
            LOG.info("Testing cbbc: secCode {}, buyPrice {}, buyQuantity {}, triggerPrice {}, trigger seqNum {}",  cbbc.code(), box(buyPrice),  box(buyQuantity),  box(triggerPrice), box(futures.orderBook().triggerInfo().triggerSeqNum()));
            messenger.responseSender().sendResponseForRequest(sender, request, ResultType.OK);
        }           
    }
	
}
