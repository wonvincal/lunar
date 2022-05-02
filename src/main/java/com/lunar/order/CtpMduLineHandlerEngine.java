package com.lunar.order;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import com.lunar.config.LineHandlerConfig;
import com.lunar.core.SystemClock;
import com.lunar.core.TimerService;
import com.lunar.message.io.sbe.OrderCancelledSbeEncoder;
import com.lunar.message.io.sbe.TradeCreatedSbeEncoder;
import com.lunar.service.ServiceConstant;

public class CtpMduLineHandlerEngine extends MatchingLineHandlerEngine {
	private final MutableDirectBuffer matchingEngineBuffer;
	private final MutableDirectBuffer matchingEngineStringBuffer;

	private OrderCancelledSbeEncoder orderCancelledEncoderMatchingEngine = new OrderCancelledSbeEncoder();
	private TradeCreatedSbeEncoder tradeCreatedEncoderMatchingEngine = new TradeCreatedSbeEncoder();

    public static MatchingLineHandlerEngine of(LineHandlerConfig config, TimerService timerService, SystemClock systemClock){
        return new CtpMduLineHandlerEngine(config.name(), timerService, systemClock);
    }

	CtpMduLineHandlerEngine(String name, TimerService timerService, SystemClock systemClock){
	    super(name, timerService, systemClock);
		this.matchingEngineBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.matchingEngineStringBuffer = new UnsafeBuffer(ByteBuffer.allocate(ServiceConstant.MAX_MESSAGE_SIZE));
		
		this.initializeMatchingEngine(MatchingEngineFactory.createCtpMduMatchingEngine(new MatchingEngine.MatchedHandler() {
			@Override
			public void onTrade(final long timestamp, final Order order, final int price, final int quantity) {
				createAndSendTrade(matchingEngineBuffer, matchingEngineStringBuffer, tradeCreatedEncoderMatchingEngine, order, price, quantity);
			}

			@Override
			public void onOrderExpired(final long timestamp, final Order order) {
			    sendOrderCancelled(matchingEngineBuffer, orderCancelledEncoderMatchingEngine, order);
			}

		}, systemClock, ServiceConstant.MATCHING_ENGINE_DELAY_NANO, true));
	}

	@Override
	public boolean isClear() {
	    return true;
	}
}
