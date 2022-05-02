package com.lunar.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.lunar.core.LunarSystem;
import com.lunar.core.LunarSystem.LunarSystemState;
import com.lunar.core.UserControlledSystemClock;
import com.lunar.exception.ConfigurationException;
import com.lunar.fsm.service.lunar.LunarServiceStateHook;
import com.lunar.fsm.service.lunar.LunarServiceWrapper;
import com.lunar.fsm.service.lunar.RemoteStateHook;
import com.lunar.fsm.service.lunar.States;
import com.lunar.message.Parameter;
import com.lunar.message.io.fbs.MessageFbsDecoder;
import com.lunar.message.io.fbs.MessagePayloadFbs;
import com.lunar.message.io.fbs.OrderFbs;
import com.lunar.message.io.fbs.OrderRequestCompletionFbs;
import com.lunar.message.io.fbs.OrderRequestCompletionTypeFbs;
import com.lunar.message.io.fbs.OrderStatusFbs;
import com.lunar.message.io.fbs.TradeFbs;
import com.lunar.message.io.fbs.TradeStatusFbs;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.OrderRequestCompletionType;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.Order;
import com.lunar.order.OrderManagementAndExecutionServiceWrapper;
import com.lunar.service.AdminServiceWrapper;
import com.lunar.service.SimpleSocket.BinaryMessageHandler;

public class LunarOrderRelatedServicesWarmupIT {
	private static final Logger LOG = LogManager.getLogger(LunarOrderRelatedServicesWarmupIT.class);
	private static String host;
	private static int port;
	private static int jmxPort;
	private static String systemName;
	private static int omesSinkId = 4;
	
	private LunarSystem system;
	private CompletableFuture<Boolean> readyFuture;
	private UserControlledSystemClock systemClock;
	
	@BeforeClass
	public static void setupClass() throws UnsupportedEncodingException, InterruptedException, ExecutionException, TimeoutException{
		host = "127.0.0.1";
		port = 8192;
		jmxPort = 8787;
		systemName = "tiger";
		System.setProperty(LunarSystem.LUNAR_HOST_PROP, host);
		System.setProperty(LunarSystem.LUNAR_PORT_PROP, String.valueOf(port));
		System.setProperty(LunarSystem.CONFIG_FILE_PROP, 
				URLDecoder.decode(LunarOrderRelatedServicesIT.class.getClassLoader().getResource(
						Paths.get("config", "it", "simple", "lunar.order.related.services.warmup.conf").toString()).getPath(), "UTF-8"));
		System.setProperty(LunarSystem.LUNAR_SYSTEM_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_AERON_DIR_PROP, systemName);
		System.setProperty(LunarSystem.LUNAR_JMX_PORT_PROP, String.valueOf(jmxPort));
	}
	
	@Ignore
	@Test
	public void test() throws InterruptedException, ExecutionException, ConfigurationException{
	    readyFuture = new CompletableFuture<Boolean>();
        systemClock = new UserControlledSystemClock(LocalDate.of(2015, 10, 2));
	    RemoteStateHook hook = new RemoteStateHook();

        // 1. Warmup on start
        AtomicBoolean isClear = new AtomicBoolean(false); 
        system = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture, hook);
        try {
            hook.hook(new LunarServiceStateHook() {
                @Override
                public void onResetExit(int sinkId, String name) {
                    if (sinkId == omesSinkId){
                        // 2. Check if state is Active
                        LunarServiceWrapper adminLunarWrapper = LunarServiceWrapper.of(system.adminService());
                        AdminServiceWrapper admin = adminLunarWrapper.adminService();
                        MessageSinkRef omesSink = system.getMessageSinkRefMgr().omes();
                        OrderManagementAndExecutionServiceWrapper omes = admin.childAsOMES(omesSink.sinkId());
                        isClear.set(omes.isClear());
                    }
                }
            });
            readyFuture.get(100000, TimeUnit.SECONDS);
        }
        catch (TimeoutException timeoutExceptoin){
            LOG.debug("Caught timeout exception");
            return;
        }
        
        // 2. Verify that all states have been cleared
        assertTrue(isClear.get());
        
        
        // 3. Verify that we are in ACTIVE state
        LunarServiceWrapper adminLunarWrapper = LunarServiceWrapper.of(system.adminService());
        AdminServiceWrapper admin = adminLunarWrapper.adminService();
        MessageSinkRef omesSink = system.getMessageSinkRefMgr().omes();
        OrderManagementAndExecutionServiceWrapper omes = admin.childAsOMES(omesSink.sinkId());
        
        assertTrue(omes.lunarService().warmupCompleted());
	    assertEquals(States.ACTIVE, omes.state());
	    
	    // 4. Send order and verify
	    WebClient webClient = new WebClient();
	    webClient.start();
	    
	       // Update purchasing power
        final long refNewPurchasingPower = 1_000_000_000;
        webClient.sendRequest("update purchasing power", RequestType.UPDATE, 
                Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
                Parameter.of(ParameterType.PURCHASING_POWER, refNewPurchasingPower));

        // Request to subscribe to all order snapshots (since web client won't get individual order update)
        webClient.sendRequest("subscribe to order and trade snapshot", RequestType.SUBSCRIBE,
            Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderAndTradeSnapshotService.value()),
            Parameter.of(ParameterType.DATA_TYPE, DataType.ALL_ORDER_AND_TRADE_UPDATE.value()));
        
		webClient.sendCommand("partial-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_PARTIAL_MATCH_ON_NEW.value()));

		Order partialFilledOrder = newOrderWithPartialMatchTrade(0, Side.BUY, 1200, webClient);
		LOG.debug("Executed quantity: {}", partialFilledOrder.cumulativeExecQty());

		webClient.sendCommand("accept-on-new", CommandType.LINE_HANDLER_ACTION,
				Parameter.of(ParameterType.SERVICE_TYPE, ServiceType.OrderManagementAndExecutionService.value()),
				Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW.value()));

		newOrder(0, Side.SELL, 100, true, webClient);
	}
	
	public Order newOrder(long secSid, Side side, int quantity, boolean expectAccept, WebClient webClient) throws InterruptedException{
	    long startTime = System.nanoTime();
	    OrderType orderType = OrderType.LIMIT_ORDER;
	    TimeInForce tif = TimeInForce.DAY;
	    BooleanType isAlgoOrder = BooleanType.FALSE;
	    int limitPrice = 100000;
	    int stopPrice = 100000;
	    int portSid = 1;

	    NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
	            webClient.owner(), 
	            secSid, 
	            orderType, 
	            quantity, 
	            side, 
	            tif, 
	            isAlgoOrder, 
	            limitPrice, 
	            stopPrice, 
	            Long.MAX_VALUE,
	            false,
	            portSid);
	    webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
	    webClient.socket().resetLatch();
	    final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
	    final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);

	    final AtomicReference<Order> order = new AtomicReference<>();

	    BinaryMessageHandler handler;
	    Supplier<Boolean> latchResultSupplier;
	    if (expectAccept){
	        handler = (b)->{
	            MessageFbsDecoder decoder = MessageFbsDecoder.of();
	            switch (decoder.init(b)){
	            case MessagePayloadFbs.OrderRequestCompletionFbs:
	                OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
	                if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
	                        && orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
	                    receivedOrderCompletionUpdate.set(true);
	                    newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
	                    LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
	                }
	                else {
	                    LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
	                            OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
	                            OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
	                }
	                break;
	            case MessagePayloadFbs.OrderFbs:
	                OrderFbs orderFbs = decoder.asOrderFbs();
	                if (orderFbs.quantity() == quantity){
	                    LOG.debug("Received new order");
	                    receivedOrderUpdate.set(true);
	                    order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
	                }
	                break;
	            }
	        };
	        latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();
	    }
	    else {
	        handler = (b)->{
	            MessageFbsDecoder decoder = MessageFbsDecoder.of();
	            switch (decoder.init(b)){
	            case MessagePayloadFbs.OrderRequestCompletionFbs:
	                OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
	                if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
	                        && orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.REJECTED){
	                    receivedOrderCompletionUpdate.set(true);
	                    newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
	                    LOG.debug("Received order request completion rejection for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
	                }
	                else {
	                    LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
	                            orderRequestCompletionFbs.completionType(),
	                            orderRequestCompletionFbs.rejectType());
	                }
	                break;
	            case MessagePayloadFbs.OrderFbs:
	                OrderFbs orderFbs = decoder.asOrderFbs();
	                if (orderFbs.status() == OrderStatusFbs.REJECTED && orderFbs.orderSid() == newOrderRequest.orderSid()){
	                    LOG.debug("Received order update for order rejected [orderSid:{}]", orderFbs.orderSid());
	                    receivedOrderUpdate.set(true);
	                }
	                break;
	            }
	        };
	        latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderUpdate.get();           
	    }
	    webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
	    webClient.socket().send(webClient.buffer().slice());
	    boolean latchResult = webClient.socket().latch().await(40000000, TimeUnit.SECONDS);
	    if (!latchResult){
	        throw new AssertionError("Not able to receive order completion and order on time");
	    }
	    LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
	    assertTrue(receivedOrderCompletionUpdate.get());
	    assertTrue(receivedOrderUpdate.get());
	    return order.get();
	}

	public Order newOrderWithPartialMatchTrade(long secSid, Side side, int quantity, WebClient webClient) throws InterruptedException {
		long startTime = LocalTime.now().toNanoOfDay();
		OrderType orderType = OrderType.LIMIT_ORDER;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = 100000;
		int stopPrice = 100000;
		int portSid = 1;
		
		NewOrderRequest newOrderRequest = NewOrderRequest.ofWithDefaults(webClient.clientKeySeq().getAndIncrement(), 
				webClient.owner(), 
				secSid, 
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice, 
				Long.MAX_VALUE,
				false,
				portSid);
		webClient.messageFbsEncoder().encodeNewOrderRequest(webClient.buffer(), newOrderRequest);
		webClient.socket().resetLatch();
	
		final AtomicBoolean receivedOrderCompletionUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderUpdate = new AtomicBoolean(false);
		final AtomicBoolean receivedPartialFilled = new AtomicBoolean(false);
		final AtomicBoolean receivedOrderExpired = new AtomicBoolean(false);
		final AtomicBoolean receivedTradeSnapshotUpdate = new AtomicBoolean(false);
		final AtomicReference<Order> order = new AtomicReference<>();
		
		BinaryMessageHandler handler = (b)->{
			MessageFbsDecoder decoder = MessageFbsDecoder.of();
			switch (decoder.init(b)){
			case MessagePayloadFbs.OrderRequestCompletionFbs:
				OrderRequestCompletionFbs orderRequestCompletionFbs = decoder.asOrderRequestCompletionFbs();
				if (orderRequestCompletionFbs.clientKey() == newOrderRequest.clientKey()
						&& orderRequestCompletionFbs.completionType() == OrderRequestCompletionTypeFbs.OK){
					receivedOrderCompletionUpdate.set(true);
					newOrderRequest.orderSid(orderRequestCompletionFbs.orderSid());
					LOG.debug("Received order request completion for new order [orderSid:{}]", orderRequestCompletionFbs.orderSid());
				}
				else {
					LOG.debug("Failed to receive order request completion [completionType:{}, rejectType:{}]", 
							OrderRequestCompletionType.get(orderRequestCompletionFbs.completionType()).name(),
							OrderRequestRejectType.get(orderRequestCompletionFbs.rejectType()).name());
				}
				break;
			case MessagePayloadFbs.OrderFbs:
				OrderFbs orderFbs = decoder.asOrderFbs();
				if (orderFbs.quantity() == quantity && orderFbs.orderSid() == newOrderRequest.orderSid()){
					if (orderFbs.status() == OrderStatusFbs.NEW){
						LOG.debug("Received new order");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedOrderUpdate.set(true);
					}
					else if (orderFbs.status() == OrderStatusFbs.PARTIALLY_FILLED){
						// It is very likely that we may not be able to receive this update because it is likely to be masked by the up-coming-expired update
						LOG.debug("Received new order partial filled");
						order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
						receivedPartialFilled.set(true);
					}
					else if (orderFbs.status() == OrderStatusFbs.EXPIRED){
						LOG.debug("Received new order expired");
						if (orderFbs.leavesQty() == 0){
							order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
							LOG.debug("Received new order expired with correct info");
							receivedOrderExpired.set(true);
						}
					}
					else if (orderFbs.status() == OrderStatusFbs.CANCELLED){
						LOG.debug("Received new order cancelled");
						if (orderFbs.leavesQty() == 0){
							order.set(Order.of(newOrderRequest, OrderStatus.get(orderFbs.status()), orderFbs.leavesQty(), orderFbs.cumulativeQty(), orderFbs.createTime(), orderFbs.updateTime()));
							LOG.debug("Received new order cancelled with correct info");
							receivedOrderExpired.set(true);
						}
					}

				}
				break;
			case MessagePayloadFbs.TradeFbs:
				TradeFbs tradeFbs = decoder.asTradeFbs();
				LOG.debug("Received new trade [orderSid:{}, orderStatus:{}, tradeStatus:{}, execPrice:{}, execQty:{}]", tradeFbs.orderSid(), OrderStatusFbs.name(tradeFbs.status()), 
						TradeStatusFbs.name(tradeFbs.tradeStatus()),
						tradeFbs.executionPrice(),
						tradeFbs.executionQty());
				if (tradeFbs.orderSid() == order.get().sid() && tradeFbs.tradeStatus() == TradeStatusFbs.NEW){
					LOG.debug("Received new trade");
					receivedTradeSnapshotUpdate.set(true);
				}
				break;
			}
		};
		Supplier<Boolean> latchResultSupplier = () -> receivedOrderCompletionUpdate.get() && receivedOrderExpired.get() && receivedTradeSnapshotUpdate.get();
		webClient.socket().binaryMessageHandler(handler).latchResultSupplier(latchResultSupplier);
		webClient.socket().send(webClient.buffer().slice());
		boolean latchResult = webClient.socket().latch().await(40000000, TimeUnit.SECONDS);
		if (!latchResult){
			throw new AssertionError("Not able to complete newOrderWithPartialMatchTrade on time");
		}
		LOG.info("Completed operation in {} ms", TimeUnit.NANOSECONDS.toMillis(LocalTime.now().toNanoOfDay() - startTime));
		assertTrue(receivedOrderCompletionUpdate.get());
		assertFalse(receivedOrderUpdate.get());
		assertTrue(receivedTradeSnapshotUpdate.get());
		if (receivedPartialFilled.get()){
			LOG.debug("Received partially filled update");
		}
		return order.get();
	}
	
	@Before
	public void setup() throws InterruptedException, ExecutionException {
	}
	
	@After
	public void cleanup(){
		system.close();
		while (system.state() != LunarSystemState.STOPPED){
			LOG.info("current state: {}, waiting...", system.state());
			LockSupport.parkNanos(500_000_000);
		}
		LOG.info("current state: {}, done...", system.state());
		assertEquals(system.state(), LunarSystemState.STOPPED);
		
		while (!system.isCompletelyShutdown()){
			LOG.info("current state: {}, waiting...", system.isCompletelyShutdown());
			LockSupport.parkNanos(500_000_000);
		}		
	}
}
