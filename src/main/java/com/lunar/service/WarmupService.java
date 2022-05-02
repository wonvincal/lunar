package com.lunar.service;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import org.HdrHistogram.Histogram;
import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.lunar.config.ServiceConfig;
import com.lunar.config.WarmupServiceConfig;
import com.lunar.core.TimerService;
import com.lunar.core.TriggerInfo;
import com.lunar.entity.Pair;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.StateTransitionEvent;
import com.lunar.message.Command;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.binary.CommandDecoder;
import com.lunar.message.binary.EventDecoder;
import com.lunar.message.binary.Handler;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestRejectType;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TimeInForce;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A service that works with Admin to warm up a particular service
 * 
 * Expected flow
 * 1) Admin Service finds out about a service that needs to be warmup
 * 2) Admin Service makes sure that the service is at warmup state
 * 3) Admin Service makes sure that Warmup Service is available
 *    If Warmup Service is not available, we should still 
 * 4)
 * 
 * @author wongca
 *
 */
public class WarmupService implements ServiceLifecycleAware {
    private static final Logger LOG = LogManager.getLogger(WarmupService.class);
	private static long HIGHEST_TRACKABLE_VALUE = 10000000000L;
	private static int NUM_SIGNIFICANT_DIGITS = 4;
	private static int EXPECTED_INTERNAL_IN_NS = 50_000_000;
	private static double INTERESTED_PERCENTILE = 99.95;

	private final byte sinkId;
	
	// OMES warmup parameters 
	private static int OMES_WARMUP_QUANTITY = 100;
	private static int OMES_WARMUP_PRICE = 10; // Equivalent to 0.01
	private static int OMES_BEFORE_ITERATIONS = 50_000;
	private static int OMES_AFTER_ITERATIONS = 150_000;
	private final TriggerInfo triggerInfo = TriggerInfo.of();
	private int triggerSeq = 1;

    private final LunarService messageService;
    private final Messenger messenger;
    private final MessageSinkRefMgr refMgr;
    private final String name;
	private final LinkedList<WarmupCommand> pendingCommands;
	private final ByteArrayOutputStream outputByteStream;
	private final PrintStream outputStream;
	private final Histogram beforeHistogram;
	private final Histogram afterHistogram;
	private final TimerService timerService;
	private final Multimap<Integer, Pair<CompletableFuture<TradeCreatedSbeDecoder>, AtomicInteger>> expectTradeCreatedFutures;
	private final Multimap<Integer, CompletableFuture<OrderCancelledSbeDecoder>> expectOrderCancelledFutures;

    public static WarmupService of(final ServiceConfig config, final LunarService messageService) {
        return new WarmupService(config, messageService);
    }

    public WarmupService(final ServiceConfig config, final LunarService messageService){
        this.name = config.name();
        this.messageService = messageService;
        this.messenger = messageService.messenger();
        this.sinkId = (byte)this.messenger.self().sinkId();
        this.timerService = messenger.timerService();
        this.refMgr = this.messenger.referenceManager();
        this.outputByteStream = new ByteArrayOutputStream(); 
        this.outputStream = new PrintStream(outputByteStream);
        if (config instanceof WarmupServiceConfig) {
            @SuppressWarnings("unused")
			final WarmupServiceConfig specificConfig = (WarmupServiceConfig)config;
        }
        else {
            throw new IllegalArgumentException("Service " + this.name + " expects a WarmupServiceConfig config");
        }
        this.pendingCommands = new LinkedList<>();
        this.beforeHistogram = new Histogram(HIGHEST_TRACKABLE_VALUE, NUM_SIGNIFICANT_DIGITS);
        this.afterHistogram = new Histogram(HIGHEST_TRACKABLE_VALUE, NUM_SIGNIFICANT_DIGITS);
        this.expectTradeCreatedFutures = ArrayListMultimap.create();
        this.expectOrderCancelledFutures = ArrayListMultimap.create();
    }
    
    @Override
    public StateTransitionEvent idleStart() {
        messenger.serviceStatusTracker().trackServiceType(ServiceType.AdminService);
        messenger.serviceStatusTracker().trackAggregatedServiceStatus((final boolean status) -> {
            if (status){
                messageService.stateEvent(StateTransitionEvent.ACTIVATE);
            }
            else { // DOWN or INITIALIZING
                messageService.stateEvent(StateTransitionEvent.WAIT);
            }
        });
        return StateTransitionEvent.WAIT;        
    }
    
	@Override
    public StateTransitionEvent activeEnter() {
		messenger.registerEventsForOrderTracker();
    	messenger.receiver().requestHandlerList().add(requestHandler);
		messenger.receiver().commandHandlerList().add(commandHandler);
		messenger.receiver().eventHandlerList().add(eventHandler);
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().add(orderAcceptedWithOrderInfoHandler);
		messenger.receiver().orderCancelledHandlerList().add(noopOrderCancelledHandler);
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().add(noopOrderRejectedWithOrderInfoHandler);
		messenger.receiver().tradeCreatedHandlerList().add(tradeCreatedHandler);
		
		messenger.receiver().newOrderRequestHandlerList().add(newOrderRequestHandler);
        messenger.receiver().strategyParamsHandlerList().add(strategyParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().add(strategyUnderlyingParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().add(strategyWarrantParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().add(strategyIssuerParamsHandler);

        return StateTransitionEvent.NULL;
    }

    @Override
    public void activeExit() {
    }

    @Override
    public StateTransitionEvent stopEnter() {
        messenger.receiver().newOrderRequestHandlerList().remove(newOrderRequestHandler);
        messenger.receiver().strategyParamsHandlerList().remove(strategyParamsHandler);
        messenger.receiver().strategyUndParamsHandlerList().remove(strategyUnderlyingParamsHandler);
        messenger.receiver().strategyWrtParamsHandlerList().remove(strategyWarrantParamsHandler);
        messenger.receiver().strategyIssuerParamsHandlerList().remove(strategyIssuerParamsHandler);
        
		messenger.receiver().requestHandlerList().remove(requestHandler);
		messenger.receiver().commandHandlerList().remove(commandHandler);
		messenger.receiver().eventHandlerList().remove(eventHandler);
		messenger.receiver().orderAcceptedWithOrderInfoHandlerList().remove(orderAcceptedWithOrderInfoHandler);
		messenger.receiver().orderCancelledHandlerList().remove(noopOrderCancelledHandler);
		messenger.receiver().orderRejectedWithOrderInfoHandlerList().remove(noopOrderRejectedWithOrderInfoHandler);
		messenger.receiver().tradeCreatedHandlerList().remove(tradeCreatedHandler);
		messenger.unregisterEventsForOrderTracker();
        return StateTransitionEvent.NULL;
    }

    private static class WarmupCommand {
    	private final MessageSinkRef sink;
    	private final ServiceType serviceType;
    	private final Command command;
    	
    	static WarmupCommand of(MessageSinkRef sink, ServiceType serviceType, Command command){
    		return new WarmupCommand(sink, serviceType, command);
    	}
    	WarmupCommand(MessageSinkRef sink, ServiceType serviceType, Command command){
    		this.sink = sink;
    		this.serviceType = serviceType;
    		this.command = command;
    	}
    	
    	@Override
    	public boolean equals(Object obj) {
    		return sink.equals(sink);
    	}
    	@Override
    	public String toString() {
    		return "sinkId:" + sink.sinkId() + ", serviceType:" + serviceType.name() + ", " + command.toString();
    	}
    }
    
    private final Handler<EventSbeDecoder> eventHandler = new Handler<EventSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder codec) {
			LOG.info("Received event [{}]", EventDecoder.decodeToString(codec));
		}
    };
    
    private final Handler<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandler = new Handler<OrderAcceptedWithOrderInfoSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder codec) {
//			LOG.info("Received order accepted [{}]", OrderAcceptedWithOrderInfoDecoder.decodeToString(codec));
		}
	};
    
	private final ObjectArrayList<Pair<CompletableFuture<TradeCreatedSbeDecoder>, AtomicInteger>> liveTradeCreatedFutures = new ObjectArrayList<>();
	private final Handler<TradeCreatedSbeDecoder> tradeCreatedHandler = new Handler<TradeCreatedSbeDecoder>() {

		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder codec) {
//			LOG.info("Received trade created [{}]", TradeCreatedDecoder.decodeToString(codec));
			
			Integer orderSid = codec.orderSid();
			if (expectTradeCreatedFutures.containsKey(orderSid)){
				
				for (Pair<CompletableFuture<TradeCreatedSbeDecoder>, AtomicInteger> future : expectTradeCreatedFutures.removeAll(orderSid)){
//					LOG.info("Complete trade created future [tradeSid:{}, orderSid:{}]", codec.tradeSid(), codec.orderSid());
					int i = future.second.decrementAndGet();
					if (i <= 0){
						future.first.complete(codec);
					}
					else {
						liveTradeCreatedFutures.add(future);
					}
				}
				for (Pair<CompletableFuture<TradeCreatedSbeDecoder>, AtomicInteger> future : liveTradeCreatedFutures){
					expectTradeCreatedFutures.put(orderSid, future);	
				}
				liveTradeCreatedFutures.clear();
			}
			else {
				LOG.error("Received unexpected trade created messages [tradeSid:{}, orderSid:{}]", codec.tradeSid(), orderSid);
			}
		}
	};
	
	private final Handler<OrderCancelledSbeDecoder> orderCancelledHandler = new Handler<OrderCancelledSbeDecoder>() {

		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
//			LOG.info("Received order cancelled [{}]", OrderCancelledDecoder.decodeToString(codec));
			
			Integer orderSid = codec.orderSid();
			if (expectOrderCancelledFutures.containsKey(orderSid)){
				for (CompletableFuture<OrderCancelledSbeDecoder> future : expectOrderCancelledFutures.removeAll(orderSid)){
//					LOG.info("Complete order cancelled future [orderSid:{}]", codec.orderSid());
					future.complete(codec);
				}
			}
			else {
				LOG.error("Received unexpected order cancelled messages [orderSid:{}]", orderSid);
			}
		}
	};

	private final Handler<OrderCancelledSbeDecoder> noopOrderCancelledHandler = new Handler<OrderCancelledSbeDecoder>() {

		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder codec) {
		}
	};

	private final Handler<OrderRejectedWithOrderInfoSbeDecoder> noopOrderRejectedWithOrderInfoHandler = new Handler<OrderRejectedWithOrderInfoSbeDecoder>() {

		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder codec) {
		}
	};
	
    private final Handler<CommandSbeDecoder> commandHandler = new Handler<CommandSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder codec) {
			byte senderSinkId = header.senderSinkId();
			try {
				MessageSinkRef sink = refMgr.get(senderSinkId); 
				switch (codec.commandType()){
				case WARMUP:{
					ImmutableListMultimap<ParameterType, Parameter> params = CommandDecoder.generateParameterMap(messenger.stringBuffer(), codec);
					ServiceType serviceType = ServiceType.get(getOneParameter(ParameterType.SERVICE_TYPE, params).valueLong().byteValue());
					switch (serviceType){
					case OrderManagementAndExecutionService:
						process(WarmupCommand.of(sink, serviceType, Command.of(senderSinkId, codec, messenger.stringBuffer())));
						break;
					default:
						LOG.error("Unsupported warmup service type [serviceType: " + serviceType.name() + ", " + CommandDecoder.decodeToString(codec) + "]");
						messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
						break;
					}
					break;
				}
				default:
					LOG.error("Unsupported operation [" + CommandDecoder.decodeToString(codec) + "]");
					messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.NOT_SUPPORTED, codec.commandType());
					break;
				}
			}
			catch (IllegalArgumentException e){
				LOG.error("Caught IllegalArgumentException while handling command", e);
				messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.INVALID_PARAMETER, codec.commandType());
				return;
			}
			catch (UnsupportedEncodingException e) {
				messenger.sendCommandAck(senderSinkId, codec.clientKey(), CommandAckType.FAILED, codec.commandType());
				return;
			}
		}
	};
	
	private static Parameter getOneParameter(ParameterType parameterType, ImmutableListMultimap<ParameterType, Parameter> items) throws IllegalArgumentException {
		ImmutableList<Parameter> parameters = items.get(parameterType);
		if (parameters.size() != 1){
			if (parameters.size() == 0){
				throw new IllegalArgumentException("Number of " + parameterType.name() + " must be 1");
			}
			StringBuilder builder = new StringBuilder();
			parameters.forEach(builder::append);
			throw new IllegalArgumentException("Number of " + parameterType.name() + " must be 1 [" + builder.toString() + "]");
		}
		return parameters.get(0);
	}

	
    private final Handler<RequestSbeDecoder> requestHandler = new Handler<RequestSbeDecoder>() {
		@Override
		public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder codec) {
		}
	};

	private WarmupCommand currentCommand;
	private CompletableFuture<Void> currentCompletionFuture;
	

	private void process(WarmupCommand command){
		if (currentCommand != null){
			pendingCommands.add(command);
			return;
		}
		processCommand(command);
	}
	
	private void processCommand(WarmupCommand command){
		currentCommand = command;
		if (currentCommand == null){
			return;
		}
		LOG.info("Processing warmup command [{}]", command);
		currentCompletionFuture = new CompletableFuture<Void>();
		currentCompletionFuture.whenComplete((value, cause) -> {
			if (cause == null){
				LOG.info("Warmup completed successfully [{}]", currentCommand);
				messenger.sendCommandAck(currentCommand.sink, currentCommand.command.clientKey(), CommandAckType.OK, currentCommand.command.commandType());
			}
			else {
				LOG.error("Warmup failed to complete [" + currentCommand + "]", cause);
				messenger.sendCommandAck(currentCommand.sink, currentCommand.command.clientKey(), CommandAckType.FAILED, currentCommand.command.commandType()); 
			}
			// Cleanup
			expectTradeCreatedFutures.clear();
			expectOrderCancelledFutures.clear();
			
			// Process the next command in queue
			processCommand(pendingCommands.pollFirst());
		});
		switch (command.serviceType){
		case OrderManagementAndExecutionService:
			warmupOMES(command, currentCompletionFuture);
			break;
		default:
			break;
		}
	}

	/**
	 * 
	 * @param command
	 * @param completionFuture
	 */
	private void warmupOMES(WarmupCommand command, CompletableFuture<Void> completionFuture){
		LOG.info("Warmup OMES - Begins");
		
		final int beforeIterations = OMES_BEFORE_ITERATIONS;
		final int afterIterations = OMES_AFTER_ITERATIONS;
		
		CompletableFuture<Void> beforeNewCompositeFuture = ex(omesNewCompositeOrderOp(command, beforeIterations, "Before Warmup", beforeHistogram, CompletableFuture.completedFuture(null)), completionFuture);
		CompletableFuture<Void> afterNewCompositeFuture = ex(omesNewCompositeOrderOp(command, afterIterations, "After Warmup", afterHistogram, beforeNewCompositeFuture), completionFuture);

		CompletableFuture<Void> beforeNewAndCancelFuture = ex(omesNewAndCancelOrderOp(command, beforeIterations, "Before Warmup", beforeHistogram, afterNewCompositeFuture), completionFuture);
		CompletableFuture<Void> afterNewAndCancelFuture = ex(omesNewAndCancelOrderOp(command, afterIterations, "After Warmup", afterHistogram, beforeNewAndCancelFuture), completionFuture);

        CompletableFuture<Void> beforeNewFuture = ex(omesNewAndRejectOrderOp(command, beforeIterations, "Before Warmup", beforeHistogram, afterNewAndCancelFuture), completionFuture);
		CompletableFuture<Void> afterNewFuture = ex(omesNewAndRejectOrderOp(command, afterIterations, "After Warmup", afterHistogram, beforeNewFuture), completionFuture);
		
		CompletableFuture<Void> beforeNewPartialAndCancelFuture = ex(omesNewPartialAndCancelledOrderOp(command, beforeIterations, "Before Warmup", beforeHistogram, afterNewFuture), completionFuture);		
		CompletableFuture<Void> afterNewPartialAndCancelFuture = ex(omesNewPartialAndCancelledOrderOp(command, afterIterations, "After Warmup", afterHistogram, beforeNewPartialAndCancelFuture), completionFuture);

        CompletableFuture<Void> beforeNewAndFilledFuture = ex(omesNewAndFilledOrderOp(command, beforeIterations, "Before Warmup", beforeHistogram, afterNewPartialAndCancelFuture), completionFuture);     
        CompletableFuture<Void> afterNewAndFilledFuture = ex(omesNewAndFilledOrderOp(command, afterIterations, "After Warmup", afterHistogram, beforeNewAndFilledFuture), completionFuture);

        afterNewAndFilledFuture.thenAccept((voidObj) -> {
			try {
				LOG.info("Completed - OMES warmup\n{}", outputByteStream.toString("UTF8"));
				outputByteStream.reset();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
			completionFuture.complete(null);
		});
	}
	
	@SuppressWarnings("unused")
	private CompletableFuture<Void> omesNewOp(WarmupCommand warmupCommand, int iterations, String comment, Histogram histogram, CompletableFuture<Void> requiredFuture){
		LOG.info("Warmup OMES - New order {} [iterations:{}]", comment, iterations);
		
		// Set purchasing power
		CompletableFuture<Void> setPurchasingPowerFuture = updatePurchasingPower(warmupCommand, requiredFuture);
		
		// Change line handler mode
		CompletableFuture<Void> lineHandlerActionFuture = omesChangeLineHandlerAction(warmupCommand, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL, setPurchasingPowerFuture);
		
		// Send order
		CompletableFuture<Void> newOrderFuture = omesNewOrder(warmupCommand, new AtomicInteger(iterations), histogram, lineHandlerActionFuture);
		
		return newOrderFuture.thenAccept((value) -> {
			try {
				final double scale = 1000.00;
				this.outputStream.format("New order roundtrip result (%s) [iterations:%d, latency(%.2f%%): %.2f microseconds]\n", comment, 
						iterations, 
						INTERESTED_PERCENTILE, 
						histogram.getValueAtPercentile(INTERESTED_PERCENTILE) / scale);
				this.outputStream.println("=========================================================================================");
				histogram.outputPercentileDistribution(this.outputStream, 1, scale);
				this.outputStream.println();
				histogram.reset();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
	
	private CompletableFuture<Void> omesNewPartialAndCancelledOrderOp(WarmupCommand warmupCommand, int iterations, String comment, Histogram histogram, CompletableFuture<Void> requiredFuture){
	    LOG.info("Warmup OMES - New, partial filled and cancelled order {} [iterations:{}]", comment, iterations);

	    // Set purchasing power
		CompletableFuture<Void> setPurchasingPowerFuture = updatePurchasingPower(warmupCommand, requiredFuture);
		
		// Change line handler mode
		CompletableFuture<Void> lineHandlerActionFuture = omesChangeLineHandlerAction(warmupCommand, LineHandlerActionType.CHANGE_MODE_PARTIAL_MATCH_WITH_MULTI_FILLS_ON_NEW, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL, setPurchasingPowerFuture);
		
		// Send order
		CompletableFuture<Void> newOrderFuture = omesNewPartialAndCancelledOrder(warmupCommand, new AtomicInteger(iterations), histogram, lineHandlerActionFuture);
		
		return newOrderFuture.thenAccept((value) -> {
			try {
				messenger.receiver().orderCancelledHandlerList().remove(orderCancelledHandler);
				messenger.receiver().orderCancelledHandlerList().add(noopOrderCancelledHandler);
				
				final double scale = 1000.00;
				this.outputStream.format("New, partial filled and cancelled order result (%s) [iterations:%d, latency(%.2f%%): %.2f microseconds]\n", comment, 
						iterations, 
						INTERESTED_PERCENTILE, 
						histogram.getValueAtPercentile(INTERESTED_PERCENTILE) / scale);
				this.outputStream.println("=========================================================================================");
				histogram.outputPercentileDistribution(this.outputStream, 1, scale);
				this.outputStream.println();
				histogram.reset();
				
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private CompletableFuture<Void> omesNewAndFilledOrderOp(WarmupCommand warmupCommand, int iterations, String comment, Histogram histogram, CompletableFuture<Void> requiredFuture){
	    LOG.info("Warmup OMES - New and filled order {} [iterations:{}]", comment, iterations);

	    // Set purchasing power
		CompletableFuture<Void> setPurchasingPowerFuture = updatePurchasingPower(warmupCommand, requiredFuture);
		
		// Change line handler mode
		CompletableFuture<Void> lineHandlerActionFuture = omesChangeLineHandlerAction(warmupCommand, LineHandlerActionType.CHANGE_MODE_MATCH_ON_NEW, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL, setPurchasingPowerFuture);
		
		// Send order
		CompletableFuture<Void> newOrderFuture = omesNewAndFilledOrder(warmupCommand, new AtomicInteger(iterations), histogram, lineHandlerActionFuture);
		
		return newOrderFuture.thenAccept((value) -> {
			try {
				final double scale = 1000.00;
				this.outputStream.format("New and filled order roundtrip result (%s) [iterations:%d, latency(%.2f%%): %.2f microseconds]\n", comment, 
						iterations, 
						INTERESTED_PERCENTILE, 
						histogram.getValueAtPercentile(INTERESTED_PERCENTILE) / scale);
				this.outputStream.println("=========================================================================================");
				histogram.outputPercentileDistribution(this.outputStream, 1, scale);
				this.outputStream.println();
				histogram.reset();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private CompletableFuture<Void> omesNewCompositeOrderOp(WarmupCommand warmupCommand, int iterations, String comment, Histogram histogram, CompletableFuture<Void> requiredFuture){
		LOG.info("Warmup OMES - New composite order {} [iterations:{}]", comment, iterations);

		// Set purchasing power
		CompletableFuture<Void> setPurchasingPowerFuture = updatePurchasingPower(warmupCommand, requiredFuture);
		// Change line handler mode
		CompletableFuture<Void> lineHandlerActionFuture = omesChangeLineHandlerAction(warmupCommand, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL, setPurchasingPowerFuture);
		// Send and cancel order
		CompletableFuture<Void> newCompositeOrderFuture = omesNewCompositeOrder(warmupCommand, new AtomicInteger(iterations), histogram, lineHandlerActionFuture);
		
		return newCompositeOrderFuture.thenAccept((value) -> {
			try {
				final double scale = 1000.00;
				this.outputStream.format("New composite order roundtrip result (%s) [iterations:%d, latency(%.2f%%): %.2f microseconds]\n", comment, 
						iterations, 
						INTERESTED_PERCENTILE, 
						histogram.getValueAtPercentile(INTERESTED_PERCENTILE) / scale);
				this.outputStream.println("=========================================================================================");
				histogram.outputPercentileDistribution(this.outputStream, 1, scale);
				this.outputStream.println();
				histogram.reset();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Send N orders to OMES
	 */
	private CompletableFuture<Void> omesNewAndCancelOrderOp(WarmupCommand warmupCommand, int iterations, String comment, Histogram histogram, CompletableFuture<Void> requiredFuture){
		LOG.info("Warmup OMES - New and cancel order {} [iterations:{}]", comment, iterations);

		// Set purchasing power
		CompletableFuture<Void> setPurchasingPowerFuture = updatePurchasingPower(warmupCommand, requiredFuture);
		// Change line handler mode
		CompletableFuture<Void> lineHandlerActionFuture = omesChangeLineHandlerAction(warmupCommand, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_NEW, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL, setPurchasingPowerFuture);
		// Send and cancel order
		CompletableFuture<Void> newAndCancelOrderFuture = omesNewAndCancelOrder(warmupCommand, new AtomicInteger(iterations), histogram, lineHandlerActionFuture);
		
		return newAndCancelOrderFuture.thenAccept((value) -> {
			try {
				final double scale = 1000.00;
				this.outputStream.format("New and cancel order roundtrip result (%s) [iterations:%d, latency(%.2f%%): %.2f microseconds]\n", comment, 
						iterations, 
						INTERESTED_PERCENTILE, 
						histogram.getValueAtPercentile(INTERESTED_PERCENTILE) / scale);
				this.outputStream.println("=========================================================================================");
				histogram.outputPercentileDistribution(this.outputStream, 1, scale);
				this.outputStream.println();
				histogram.reset();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	private CompletableFuture<Void> omesNewAndRejectOrderOp(WarmupCommand warmupCommand, int iterations, String comment, Histogram histogram, CompletableFuture<Void> requiredFuture){
		LOG.info("Warmup OMES - New and reject order {} [iterations:{}]", comment, iterations);

		// Set purchasing power
		CompletableFuture<Void> setPurchasingPowerFuture = updatePurchasingPower(warmupCommand, requiredFuture);
		// Change line handler mode
		CompletableFuture<Void> lineHandlerActionFuture = omesChangeLineHandlerAction(warmupCommand, LineHandlerActionType.CHANGE_MODE_REJECT_ON_NEW, LineHandlerActionType.CHANGE_MODE_ACCEPT_ON_CANCEL, setPurchasingPowerFuture);
		// Send and cancel order
		CompletableFuture<Void> newAndRejectOrderFuture = omesNewAndRejectOrder(warmupCommand, new AtomicInteger(iterations), histogram, lineHandlerActionFuture);
		
		return newAndRejectOrderFuture.thenAccept((value) -> {
			try {
				final double scale = 1000.00;
				this.outputStream.format("New and reject order roundtrip result (%s) [iterations:%d, latency(%.2f%%): %.2f microseconds]\n", comment, 
						iterations, 
						INTERESTED_PERCENTILE, 
						histogram.getValueAtPercentile(INTERESTED_PERCENTILE) / scale);
				this.outputStream.println("=========================================================================================");
				histogram.outputPercentileDistribution(this.outputStream, 1, scale);
				this.outputStream.println();
				histogram.reset();
			} 
			catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
	
	private <T, U> CompletableFuture<T> ex(CompletableFuture<T> future, CompletableFuture<U> exFuture){
		future.exceptionally(new Function<Throwable, T>() {
			@Override
			public T apply(Throwable t) {
				exFuture.completeExceptionally(t);
				return null;
			}
		});
		return future;
	}
	
	private CompletableFuture<Void> updatePurchasingPower(WarmupCommand warmupCommand, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> opFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> {
	        LOG.info("Warmup OMES - Send update of purchasing power");
			long purchasingPower = Long.MAX_VALUE / 1000;
			ex(messenger.sendRequest(warmupCommand.sink, RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, purchasingPower))), opFuture)
			.thenAccept((request) -> {
				if (request.resultType() != ResultType.OK){
					opFuture.completeExceptionally(new Exception("Unexpected result type [resultType:" + request.resultType().name() + "]"));
					return;
				}
				opFuture.complete(null);				
			});
		});
		return opFuture;
	}
	
	/**
	 * 
	 * @param warmupCommand
	 * @param requiredFuture This method should begin only when the requiredFuture has completed successfully
	 * @param completionFuture This method should complete this future either normally or exceptionally when the action is done
	 * @return CompletableFuture that tells if this action has been completed
	 */
	private CompletableFuture<Void> omesChangeLineHandlerAction(WarmupCommand warmupCommand, LineHandlerActionType newActionType, LineHandlerActionType cancelActionType, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> opFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> {
            LOG.debug("Sending command to change line handler mode");
			// Change line handler new mode
			Command newActionCommand = createLineHandlerCommand(messenger, warmupCommand.sink, newActionType);
			ex(messenger.sendCommand(warmupCommand.sink, newActionCommand), opFuture)
			.thenAccept((newAction)->{
				if (newAction.ackType() != CommandAckType.OK){
					opFuture.completeExceptionally(new Exception("Command nack for accept-on-new"));
					return;
				}
				// Change line handler cancel mode
				Command cancelActionCommand = createLineHandlerCommand(messenger, warmupCommand.sink, cancelActionType);
				ex(messenger.sendCommand(warmupCommand.sink, cancelActionCommand), opFuture)
				.thenAccept((cancelAction)->{
					if (cancelAction.ackType() != CommandAckType.OK){
						opFuture.completeExceptionally(new Exception("Command nack for accept-on-cancel"));
						return;
					}
					opFuture.complete(null);
				});
			});			
		});
		return opFuture;
	}
	
	private static Command createLineHandlerCommand(Messenger messenger, MessageSinkRef sink, LineHandlerActionType actionType){
		return Command.of(sink.sinkId(), 
				messenger.getNextClientKeyAndIncrement(), 
				CommandType.LINE_HANDLER_ACTION, 
				Parameters.listOf(Parameter.of(ParameterType.LINE_HANDLER_ACTION_TYPE,
						actionType.value())));		
	}
	
	private CompletableFuture<Void> omesNewOrder(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> actionFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> { omesNewOrderImpl(command, iterations, histogram, actionFuture);});
		return actionFuture;
	}

	private void omesNewOrderImpl(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> completionFuture){
		// Send new order request to OMES
		final int quantity = OMES_WARMUP_QUANTITY;
		Side side = Side.BUY;
		OrderType orderType = OrderType.LIMIT_ORDER;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = OMES_WARMUP_PRICE;
		int stopPrice = OMES_WARMUP_PRICE;
		int portSid = -1;
		long secSid = ServiceConstant.WARMUP_SECURITY_SID;
		long timeoutNs = Long.MAX_VALUE;

		final long startTimeSystemNs = timerService.nanoTime();
		triggerInfo.triggerNanoOfDay(startTimeSystemNs);
		triggerInfo.triggerSeqNum(triggerSeq++);
		triggerInfo.triggeredBy(this.sinkId);
		
		MessageSinkRef omes = command.sink;
		int clientKey = messenger.getNextClientKeyAndIncrement();
		NewOrderRequest newOrderRequest = NewOrderRequest.of(clientKey, 
				messenger.self(),
				secSid,
				orderType, 
				quantity, 
				side,
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice,
				timeoutNs,
				false,
				clientKey % 2,
				portSid,
				triggerInfo);
		
		CompletableFuture<OrderRequest> newOrderFuture = ex(messenger.sendNewOrder(omes, newOrderRequest), completionFuture);
		newOrderFuture.thenAccept(new Consumer<OrderRequest>() {

			@Override
			public void accept(OrderRequest on) {
				if (on.rejectType() != OrderRequestRejectType.VALID_AND_NOT_REJECT){
					completionFuture.completeExceptionally(new Exception("Failed to perform new order request [rejectType:" + on.rejectType().name() + "]"));
					return;
				}

				histogram.recordValueWithExpectedInterval(timerService.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
				if (iterations.decrementAndGet() != 0){
					// Call itself
					omesNewOrderImpl(command, iterations, histogram, completionFuture);
				}
				else {
					completionFuture.complete(null);
				}
			}
		});
	}

	private CompletableFuture<Void> omesNewAndFilledOrder(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> actionFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> {
		      // Make sure next sequence number if even number
	        int key = messenger.getNextClientKeyAndIncrement();
	        if ((key & 1) == 0){
	            key = messenger.getNextClientKeyAndIncrement();
	        }

		    omesNewAndFilledOrderImpl(command, iterations, histogram, actionFuture);
		});
		return actionFuture;
	}
	
	private CompletableFuture<Void> omesNewPartialAndCancelledOrder(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> actionFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> {
			messenger.receiver().orderCancelledHandlerList().remove(noopOrderCancelledHandler);
			messenger.receiver().orderCancelledHandlerList().add(orderCancelledHandler);
			
		      // Make sure next sequence number if even number
	        int key = messenger.getNextClientKeyAndIncrement();
	        if ((key & 1) == 0){
	            key = messenger.getNextClientKeyAndIncrement();
	        }

	        omesNewPartialAndCancelledOrderImpl(command, iterations, histogram, actionFuture);
		});
		return actionFuture;
	}
	
	private void omesNewPartialAndCancelledOrderImpl(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> completionFuture){
		// Send new order request to OMES
		OrderType orderType = OrderType.LIMIT_ORDER;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = OMES_WARMUP_PRICE;
		int stopPrice = OMES_WARMUP_PRICE;
		int portSid = -1;
		long secSid = ServiceConstant.WARMUP_SECURITY_SID;
		long timeoutNs = Long.MAX_VALUE;

		final int clientKey = messenger.getNextClientKeyAndIncrement();
        Side side = (clientKey & 1) == 0 ? Side.BUY : Side.SELL;
        int quantity = (clientKey & 1) == 0 ? (OMES_WARMUP_QUANTITY + OMES_WARMUP_QUANTITY + OMES_WARMUP_QUANTITY) : OMES_WARMUP_QUANTITY + OMES_WARMUP_QUANTITY;
        int expectedNumTrades = (clientKey & 1) == 0 ? 2 : 1;
		final long startTimeSystemNs = timerService.nanoTime();
		triggerInfo.triggerNanoOfDay(startTimeSystemNs);
		triggerInfo.triggerSeqNum(triggerSeq++);
		triggerInfo.triggeredBy(this.sinkId);

		MessageSinkRef omes = command.sink;
		NewOrderRequest newOrderRequest = NewOrderRequest.of(clientKey, 
				messenger.self(),
				secSid,
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice,
				timeoutNs,
				false,
				0,
				portSid,
				triggerInfo);
		
		CompletableFuture<OrderRequest> newOrderFuture = ex(messenger.sendNewOrder(omes, newOrderRequest), completionFuture);
		newOrderFuture.thenAccept(new Consumer<OrderRequest>() {

			@Override
			public void accept(OrderRequest on) {
				if (on.rejectType() != OrderRequestRejectType.VALID_AND_NOT_REJECT){
					completionFuture.completeExceptionally(new Exception("Failed to perform new order request [rejectType:" + on.rejectType().name() + "]"));
					return;
				}
				
//		        LOG.debug("New order accepted: [clientKey:{}, orderSid:{}]", on.clientKey(), on.orderSid());

		        CompletableFuture<TradeCreatedSbeDecoder> tradeCreatedFuture = ex(new CompletableFuture<TradeCreatedSbeDecoder>(), completionFuture);
				tradeCreatedFuture.thenAccept((trade) -> {
//				    LOG.debug("New trades created: [clientKey:{}, orderSid:{}, side:{}]", on.clientKey(), on.orderSid(), trade.side());
				    
				    CompletableFuture<OrderCancelledSbeDecoder> orderCancelledFuture = ex(new CompletableFuture<OrderCancelledSbeDecoder>(), completionFuture);
				    orderCancelledFuture.thenAccept((cancelled) -> {
//				    	LOG.debug("New order cancelled: [clientKey:{}, orderSid:{}]", on.clientKey(), cancelled.orderSid());
						histogram.recordValueWithExpectedInterval(timerService.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
						if (iterations.decrementAndGet() != 0){
							// Call itself
							omesNewPartialAndCancelledOrderImpl(command, iterations, histogram, completionFuture);
						}
						else {
							completionFuture.complete(null);
						}				    	
				    });
				    expectOrderCancelledFutures.put(on.orderSid(), orderCancelledFuture);
				}); 
				expectTradeCreatedFutures.put(on.orderSid(), Pair.of(tradeCreatedFuture, new AtomicInteger(expectedNumTrades)));				
			}
		});
	}
	
	private void omesNewAndFilledOrderImpl(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> completionFuture){
		// Send new order request to OMES
		int quantity = OMES_WARMUP_QUANTITY;
		OrderType orderType = OrderType.ENHANCED_LIMIT_ORDER;
		TimeInForce tif = TimeInForce.FILL_AND_KILL;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = OMES_WARMUP_PRICE;
		int stopPrice = OMES_WARMUP_PRICE;
		int portSid = -1;
		long secSid = ServiceConstant.WARMUP_SECURITY_SID;
		long timeoutNs = Long.MAX_VALUE;

		final int clientKey = messenger.getNextClientKeyAndIncrement();
        Side side = (clientKey & 1) == 0 ? Side.BUY : Side.SELL;
		final long startTimeSystemNs = timerService.nanoTime();
		MessageSinkRef omes = command.sink;
		
		triggerInfo.triggerNanoOfDay(startTimeSystemNs);
		triggerInfo.triggerSeqNum(triggerSeq++);
		triggerInfo.triggeredBy(this.sinkId);
		
		NewOrderRequest newOrderRequest = NewOrderRequest.of(clientKey, 
				messenger.self(),
				secSid,
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice,
				timeoutNs,
				false,
				0,
				portSid,
				triggerInfo);
		
		CompletableFuture<OrderRequest> newOrderFuture = ex(messenger.sendNewOrder(omes, newOrderRequest), completionFuture);
		newOrderFuture.thenAccept(new Consumer<OrderRequest>() {

			@Override
			public void accept(OrderRequest on) {
				if (on.rejectType() != OrderRequestRejectType.VALID_AND_NOT_REJECT){
					completionFuture.completeExceptionally(new Exception("Failed to perform new order request [rejectType:" + on.rejectType().name() + "]"));
					return;
				}
				
//		        LOG.debug("New order accepted: [clientKey:{}, orderSid:{}]", on.clientKey(), on.orderSid());

		        CompletableFuture<TradeCreatedSbeDecoder> tradeCreatedFuture = ex(new CompletableFuture<TradeCreatedSbeDecoder>(), completionFuture);
				tradeCreatedFuture.thenAccept((trade) -> {
//				    LOG.debug("New trade created: [clientKey:{}, orderSid:{}, side:{}]", on.clientKey(), on.orderSid(), trade.side());
					histogram.recordValueWithExpectedInterval(timerService.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
					if (iterations.decrementAndGet() != 0){
						// Call itself
						omesNewAndFilledOrderImpl(command, iterations, histogram, completionFuture);
					}
					else {
						completionFuture.complete(null);
					}
				}); 
				expectTradeCreatedFutures.put(on.orderSid(), Pair.of(tradeCreatedFuture, new AtomicInteger(1)));				
			}
		});
	}

	private CompletableFuture<Void> omesNewAndRejectOrder(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> actionFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> { omesNewAndRejectOrderImpl(command, iterations, histogram, actionFuture);});
		return actionFuture;
	}
	
	private void omesNewAndRejectOrderImpl(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> completionFuture){
		// Send new order request to OMES
		final int quantity = OMES_WARMUP_QUANTITY;
		Side side = Side.BUY;
		OrderType orderType = OrderType.LIMIT_ORDER;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = OMES_WARMUP_PRICE;
		int stopPrice = OMES_WARMUP_PRICE;
		int portSid = -1;
		long secSid = ServiceConstant.WARMUP_SECURITY_SID;
		long timeoutNs = Long.MAX_VALUE;

		final long startTimeSystemNs = timerService.nanoTime();
		triggerInfo.triggerNanoOfDay(startTimeSystemNs);
		triggerInfo.triggerSeqNum(triggerSeq++);
		triggerInfo.triggeredBy(this.sinkId);

		MessageSinkRef omes = command.sink;
		int clientKey = messenger.getNextClientKeyAndIncrement();
		NewOrderRequest newOrderRequest = NewOrderRequest.of(clientKey, 
				messenger.self(),
				secSid,
				orderType, 
				quantity, 
				side,
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice,
				timeoutNs,
				false,
				0,
				portSid,
				triggerInfo);
		
		CompletableFuture<OrderRequest> newOrderFuture = ex(messenger.sendNewOrder(omes, newOrderRequest), completionFuture);
		newOrderFuture.thenAccept(new Consumer<OrderRequest>() {

			@Override
			public void accept(OrderRequest on) {
				if (on.rejectType() == OrderRequestRejectType.VALID_AND_NOT_REJECT){
					completionFuture.completeExceptionally(new Exception("Failed to reject order request"));
					return;
				}

				histogram.recordValueWithExpectedInterval(timerService.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
				if (iterations.decrementAndGet() != 0){
					// Call itself
					omesNewAndRejectOrderImpl(command, iterations, histogram, completionFuture);
				}
				else {
					completionFuture.complete(null);
				}
			}
		});
	}
	
	private CompletableFuture<Void> omesNewCompositeOrder(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> actionFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> { omesNewCompositeOrderImpl(command, iterations, histogram, actionFuture);});
		return actionFuture;
	}
	
	private void omesNewCompositeOrderImpl(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> completionFuture){
		// Send new order request to OMES
		int quantity = OMES_WARMUP_QUANTITY;
		Side side = Side.BUY;
		OrderType orderType = OrderType.LIMIT_THEN_CANCEL_ORDER;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = OMES_WARMUP_PRICE;
		int stopPrice = OMES_WARMUP_PRICE;
		int portSid = -1;
		long secSid = ServiceConstant.WARMUP_SECURITY_SID;
		long timeoutNs = Long.MAX_VALUE;

		final long startTimeSystemNs = timerService.nanoTime();
		triggerInfo.triggerNanoOfDay(startTimeSystemNs);
		triggerInfo.triggerSeqNum(triggerSeq++);
		triggerInfo.triggeredBy(this.sinkId);

		MessageSinkRef omes = command.sink;
		NewOrderRequest newOrderRequest = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
				messenger.self(),
				secSid,
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice,
				timeoutNs,
				false,
				0,
				portSid,
				triggerInfo);
		
		CompletableFuture<OrderRequest> newOrderFuture = ex(messenger.sendNewCompositeOrder(omes, newOrderRequest), completionFuture);
		newOrderFuture.thenAccept(new Consumer<OrderRequest>() {

			@Override
			public void accept(OrderRequest on) {
				if (on.rejectType() != OrderRequestRejectType.VALID_AND_NOT_REJECT){
					completionFuture.completeExceptionally(new Exception("Failed to perform new composite order request [rejectType:" + on.rejectType().name() + "]"));
					return;
				}
				histogram.recordValueWithExpectedInterval(timerService.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
				if (iterations.decrementAndGet() != 0){
					omesNewCompositeOrderImpl(command, iterations, histogram, completionFuture);
				}
				else{
					completionFuture.complete(null);
				}					
			}
		});
	}
	
	private CompletableFuture<Void> omesNewAndCancelOrder(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> requiredFuture){
		CompletableFuture<Void> actionFuture = new CompletableFuture<Void>();
		requiredFuture.thenAccept((voidObj) -> { omesNewAndCancelOrderImpl(command, iterations, histogram, actionFuture);});
		return actionFuture;
	}
	
	private void omesNewAndCancelOrderImpl(WarmupCommand command, AtomicInteger iterations, Histogram histogram, CompletableFuture<Void> completionFuture){
		// Send new order request to OMES
		int quantity = OMES_WARMUP_QUANTITY;
		Side side = Side.BUY;
		OrderType orderType = OrderType.LIMIT_ORDER;
		TimeInForce tif = TimeInForce.DAY;
		BooleanType isAlgoOrder = BooleanType.FALSE;
		int limitPrice = OMES_WARMUP_PRICE;
		int stopPrice = OMES_WARMUP_PRICE;
		int portSid = -1;
		long secSid = ServiceConstant.WARMUP_SECURITY_SID;
		long timeoutNs = Long.MAX_VALUE;

		final long startTimeSystemNs = timerService.nanoTime();
		triggerInfo.triggerNanoOfDay(startTimeSystemNs);
		triggerInfo.triggerSeqNum(triggerSeq++);
		triggerInfo.triggeredBy(this.sinkId);

		MessageSinkRef omes = command.sink;
		NewOrderRequest newOrderRequest = NewOrderRequest.of(messenger.getNextClientKeyAndIncrement(), 
				messenger.self(),
				secSid,
				orderType, 
				quantity, 
				side, 
				tif, 
				isAlgoOrder, 
				limitPrice, 
				stopPrice,
				timeoutNs,
				false,
				0,
				portSid,
				triggerInfo);
		
		CompletableFuture<OrderRequest> newOrderFuture = ex(messenger.sendNewOrder(omes, newOrderRequest), completionFuture);
		newOrderFuture.thenAccept(new Consumer<OrderRequest>() {

			@Override
			public void accept(OrderRequest on) {
				if (on.rejectType() != OrderRequestRejectType.VALID_AND_NOT_REJECT){
					completionFuture.completeExceptionally(new Exception("Failed to perform new order request [rejectType:" + on.rejectType().name() + "]"));
					return;
				}
				
				CancelOrderRequest cancelOrderRequest = CancelOrderRequest.of(messenger.getNextClientKeyAndIncrement(),
						messenger.self(), 
						on.orderSid(),
						secSid,
						on.asNewOrderRequest().side());
				
				CompletableFuture<OrderRequest> cancelOrderFuture = ex(messenger.sendCancelOrder(omes, cancelOrderRequest), completionFuture);
				cancelOrderFuture.thenAccept((oc) -> {
					if (oc.rejectType() != OrderRequestRejectType.VALID_AND_NOT_REJECT){
						completionFuture.completeExceptionally(new Exception("Failed to cancel order request [orderSid:" + oc.orderSid() + ", rejectType:" + oc.rejectType().name() + "]"));
						return;
					}
					histogram.recordValueWithExpectedInterval(timerService.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
					if (iterations.decrementAndGet() != 0){
						// Call itself
						omesNewAndCancelOrderImpl(command, iterations, histogram, completionFuture);
					}
					else {
						completionFuture.complete(null);
					}					
				});
			}
		});
	}
	
    private final Handler<NewOrderRequestSbeDecoder> newOrderRequestHandler = new Handler<NewOrderRequestSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder request) {
            
        }
    };
    
    private Handler<StrategyParamsSbeDecoder> strategyParamsHandler = new Handler<StrategyParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyParamsSbeDecoder strategyParams) {
            
        }
    };
    
    private Handler<StrategyUndParamsSbeDecoder> strategyUnderlyingParamsHandler = new Handler<StrategyUndParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder strategyUndParams) {
            
        }
    };
    
    private Handler<StrategyWrtParamsSbeDecoder> strategyWarrantParamsHandler = new Handler<StrategyWrtParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder strategyWrtParams) {
            
        }
    };
    
    private Handler<StrategyIssuerParamsSbeDecoder> strategyIssuerParamsHandler = new Handler<StrategyIssuerParamsSbeDecoder>() {
        @Override
        public void handle(DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder strategyIssuerParams) {
            
        }
    };

}
