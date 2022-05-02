package com.lunar.message.binary;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.journal.JournalReader;
import com.lunar.journal.io.sbe.JournalRecordSbeDecoder;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AmendOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.DividendCurveSbeDecoder;
import com.lunar.message.io.sbe.EchoSbeDecoder;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.ExchangeSbeDecoder;
import com.lunar.message.io.sbe.FragmentInitSbeDecoder;
import com.lunar.message.io.sbe.FragmentSbeDecoder;
import com.lunar.message.io.sbe.GenericTrackerSbeDecoder;
import com.lunar.message.io.sbe.GreeksSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketDataTradeSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MarketStatusSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderRequestCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.PingSbeDecoder;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.RiskControlSbeDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.service.ServiceConstant;

public class JournalPrinter {
	static final Logger LOG = LogManager.getLogger(JournalPrinter.class);
	private final MutableDirectBuffer buffer;
	private final Decoder[] decoders;
	private PrintStream outputStream; 
	private Consumer<PrintStream> header;
	private final Object[] userSupplied;
	
	// hot decoders
	private final AggregateOrderBookUpdateDecoder aggregateOrderBookUpdateDecoder = AggregateOrderBookUpdateDecoder.of();
	private final OrderBookSnapshotDecoder orderBookSnapshotDecoder = OrderBookSnapshotDecoder.of();
	private final NewOrderRequestDecoder newOrderRequestDecoder = NewOrderRequestDecoder.of();
	private final AmendOrderRequestDecoder amendOrderRequestDecoder = AmendOrderRequestDecoder.of();
	private final CancelOrderRequestDecoder cancelOrderRequestDecoder = CancelOrderRequestDecoder.of();
	private final OrderCompletionDecoder orderCompletionDecoder = OrderCompletionDecoder.of();
	private final OrderRequestCompletionDecoder orderRequestCompletionDecoder = OrderRequestCompletionDecoder.of();
	private final OrderRequestAcceptedDecoder orderRequestAcceptedDecoder = OrderRequestAcceptedDecoder.of();
	private final TimerEventDecoder timerEventDecoder = TimerEventDecoder.of();
	private final MarketDataTradeDecoder marketDataTradeDecoder = MarketDataTradeDecoder.of();
	private final BoobsDecoder boobsDecoder = BoobsDecoder.of();
	private final MarketStatusDecoder marketStatusDecoder = MarketStatusDecoder.of();
	private final ExchangeDecoder exchangeDecoder = ExchangeDecoder.of();
	private final IssuerDecoder issuerDecoder = IssuerDecoder.of();
	private final SecurityDecoder securityDecoder = SecurityDecoder.of();
	private final StrategyTypeDecoder stratTypeDecoder = StrategyTypeDecoder.of();
    private final StrategySwitchDecoder strategySwitchDecoder = StrategySwitchDecoder.of();
    private final StrategyParamsDecoder strategyParamsDecoder = StrategyParamsDecoder.of();
    private final StrategyUndParamsDecoder strategyUndParamsDecoder = StrategyUndParamsDecoder.of();
    private final StrategyWrtParamsDecoder strategyWrtParamsDecoder = StrategyWrtParamsDecoder.of();
    private final StrategyIssuerParamsDecoder strategyIssuerParamsDecoder = StrategyIssuerParamsDecoder.of();
    private final GenericTrackerDecoder genericTrackerDecoder = GenericTrackerDecoder.of();
    private final DividendCurveDecoder dividendCurveDecoder = DividendCurveDecoder.of();
    private final GreeksDecoder greeksDecoder = GreeksDecoder.of();
    private final MarketStatsDecoder marketStatsDecoder = MarketStatsDecoder.of();

    // Orders
	private final OrderAcceptedDecoder orderAcceptedDecoder = OrderAcceptedDecoder.of();
	private final OrderAcceptedWithOrderInfoDecoder orderAcceptedWithOrderInfoDecoder = OrderAcceptedWithOrderInfoDecoder.of();
	private final OrderCancelledDecoder orderCancelledDecoder = OrderCancelledDecoder.of();
	private final OrderCancelledWithOrderInfoDecoder orderCancelledWithOrderInfoDecoder = OrderCancelledWithOrderInfoDecoder.of();
	private final OrderExpiredDecoder orderExpiredDecoder = OrderExpiredDecoder.of();
	private final OrderExpiredWithOrderInfoDecoder orderExpiredWithOrderInfoDecoder = OrderExpiredWithOrderInfoDecoder.of();
	private final OrderRejectedDecoder orderRejectedDecoder = OrderRejectedDecoder.of();
	private final OrderRejectedWithOrderInfoDecoder orderRejectedWithOrderInfoDecoder = OrderRejectedWithOrderInfoDecoder.of();
	private final TradeCreatedDecoder tradeCreatedDecoder = TradeCreatedDecoder.of();
	private final TradeCreatedWithOrderInfoDecoder tradeCreatedWithOrderInfoDecoder = TradeCreatedWithOrderInfoDecoder.of(); 
	private final TradeCancelledDecoder tradeCancelledDecoder = TradeCancelledDecoder.of();
	private final OrderDecoder orderDecoder = OrderDecoder.of();
	private final TradeDecoder tradeDecoder = TradeDecoder.of();
	private final OrderCancelRejectedDecoder orderCancelRejectedDecoder = OrderCancelRejectedDecoder.of();
	private final OrderCancelRejectedWithOrderInfoDecoder orderCancelRejectedWithOrderInfoDecoder = OrderCancelRejectedWithOrderInfoDecoder.of();
	private final OrderAmendedDecoder orderAmendedDecoder = OrderAmendedDecoder.of();
	private final OrderAmendRejectedDecoder orderAmendRejectedDecoder = OrderAmendRejectedDecoder.of();
	
	// cold decoders
	private final FragmentDecoder fragmentDecoder = FragmentDecoder.of();
	private final FragmentInitDecoder fragmentInitDecoder = FragmentInitDecoder.of(); 
	private final RequestDecoder requestDecoder = RequestDecoder.of();
	private final ResponseDecoder responseDecoder;
	private final ServiceStatusDecoder serviceStatusDecoder = ServiceStatusDecoder.of();
	private final CommandDecoder commandDecoder = CommandDecoder.of();
	private final CommandAckDecoder commandAckDecoder = CommandAckDecoder.of();
	private final PingDecoder pingDecoder = PingDecoder.of();
	private final EchoDecoder echoDecoder = EchoDecoder.of();
	private final RiskStateDecoder riskStateDecoder = RiskStateDecoder.of();
	private final RiskControlDecoder riskControlDecoder = RiskControlDecoder.of();
	private final PositionDecoder positionDecoder = PositionDecoder.of();
	private final EventDecoder eventDecoder = EventDecoder.of();
	
	// entity decoder
	private final EntityDecoderManager entityDecoderMgr;
	
	private final Handler<AggregateOrderBookUpdateSbeDecoder> noopAggregateOrderBookUpdateHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder payload) ->{};
	private final Handler<OrderBookSnapshotSbeDecoder> noopOrderBookSnapshotHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder payload) ->{};
	private final Handler<NewOrderRequestSbeDecoder> noopNewOrderRequestHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, NewOrderRequestSbeDecoder payload) ->{};
	private final Handler<AmendOrderRequestSbeDecoder> noopAmendOrderRequestHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, AmendOrderRequestSbeDecoder payload) ->{};
	private final Handler<CancelOrderRequestSbeDecoder> noopCancelOrderRequestHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, CancelOrderRequestSbeDecoder payload) ->{};
	private final Handler<OrderCompletionSbeDecoder> noopOrderCompletionHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCompletionSbeDecoder payload) ->{};
	private final Handler<OrderRequestCompletionSbeDecoder> noopOrderRequestCompletionHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestCompletionSbeDecoder payload) ->{};
	private final Handler<OrderRequestAcceptedSbeDecoder> noopOrderRequestAcceptedHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRequestAcceptedSbeDecoder payload) ->{};
	private final Handler<TimerEventSbeDecoder> noopTimerEventHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, TimerEventSbeDecoder payload) ->{};
	private final Handler<MarketDataTradeSbeDecoder> noopMarketDataTradeHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketDataTradeSbeDecoder payload) ->{};
	private final Handler<BoobsSbeDecoder> noopBoobsHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, BoobsSbeDecoder payload) ->{};
	private final Handler<MarketStatusSbeDecoder> noopMarketStatusHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatusSbeDecoder payload) ->{};
	private final Handler<ExchangeSbeDecoder> noopExchangeHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, ExchangeSbeDecoder payload) ->{};
	private final Handler<IssuerSbeDecoder> noopIssuerHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, IssuerSbeDecoder payload) ->{};
	private final Handler<SecuritySbeDecoder> noopSecurityHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, SecuritySbeDecoder payload) ->{};
	private final Handler<StrategyTypeSbeDecoder> noopStrategyTypeHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyTypeSbeDecoder payload) ->{};
    private final Handler<StrategySwitchSbeDecoder> noopStrategySwitchHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategySwitchSbeDecoder payload) ->{};
    private final Handler<StrategyParamsSbeDecoder> noopStrategyParamsHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyParamsSbeDecoder payload) ->{};
    private final Handler<StrategyUndParamsSbeDecoder> noopStrategyUndParamsHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder payload) ->{};
    private final Handler<StrategyWrtParamsSbeDecoder> noopStrategyWrtParamsHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder payload) ->{};
    private final Handler<StrategyIssuerParamsSbeDecoder> noopStrategyIssuerParamsHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder payload) ->{};
    private final Handler<GenericTrackerSbeDecoder> noopGenericTrackerHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, GenericTrackerSbeDecoder payload) ->{};
    private final Handler<DividendCurveSbeDecoder> noopDividendCurveHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, DividendCurveSbeDecoder payload) ->{};
    private final Handler<GreeksSbeDecoder> noopGreeksHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, GreeksSbeDecoder payload) ->{};
    private final Handler<MarketStatsSbeDecoder> noopMarketStatsHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, MarketStatsSbeDecoder payload) ->{};

    // Orders
	private final Handler<OrderAcceptedSbeDecoder> noopOrderAcceptedHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedSbeDecoder payload) ->{};
	private final Handler<OrderAcceptedWithOrderInfoSbeDecoder> noopOrderAcceptedWithOrderInfoHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAcceptedWithOrderInfoSbeDecoder payload) ->{};
	private final Handler<OrderCancelledSbeDecoder> noopOrderCancelledHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledSbeDecoder payload) ->{};
	private final Handler<OrderCancelledWithOrderInfoSbeDecoder> noopOrderCancelledWithOrderInfoHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelledWithOrderInfoSbeDecoder payload) ->{};
	private final Handler<OrderExpiredSbeDecoder> noopOrderExpiredHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredSbeDecoder payload) ->{};
	private final Handler<OrderExpiredWithOrderInfoSbeDecoder> noopOrderExpiredWithOrderInfoHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderExpiredWithOrderInfoSbeDecoder payload) ->{};
	private final Handler<OrderRejectedSbeDecoder> noopOrderRejectedHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedSbeDecoder payload) ->{};
	private final Handler<OrderRejectedWithOrderInfoSbeDecoder> noopOrderRejectedWithOrderInfoHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderRejectedWithOrderInfoSbeDecoder payload) ->{};
	private final Handler<TradeCreatedSbeDecoder> noopTradeCreatedHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedSbeDecoder payload) ->{};
	private final Handler<TradeCreatedWithOrderInfoSbeDecoder> noopTradeCreatedWithOrderInfoHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCreatedWithOrderInfoSbeDecoder payload) ->{};
	private final Handler<TradeCancelledSbeDecoder> noopTradeCancelledHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeCancelledSbeDecoder payload) ->{};
	private final Handler<OrderSbeDecoder> noopOrderHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderSbeDecoder payload) ->{};
	private final Handler<TradeSbeDecoder> noopTradeHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, TradeSbeDecoder payload) ->{};
	private final Handler<OrderCancelRejectedSbeDecoder> noopOrderCancelRejectedHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedSbeDecoder payload) ->{};
	private final Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> noopOrderCancelRejectedWithOrderInfoHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderCancelRejectedWithOrderInfoSbeDecoder payload) ->{};
	private final Handler<OrderAmendedSbeDecoder> noopOrderAmendedHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendedSbeDecoder payload) ->{};
	private final Handler<OrderAmendRejectedSbeDecoder> noopOrderAmendRejectedHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, OrderAmendRejectedSbeDecoder payload) ->{};
	
	// cold SbeDecoders
	private final Handler<FragmentSbeDecoder> noopFragmentHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, FragmentSbeDecoder payload) ->{};
	private final Handler<FragmentInitSbeDecoder> noopFragmentInitHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, FragmentInitSbeDecoder payload) ->{};
	private final Handler<RequestSbeDecoder> noopRequestHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, RequestSbeDecoder payload) ->{};
	private final Handler<ResponseSbeDecoder> noopResponseHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder payload) ->{};
	private final Handler<ServiceStatusSbeDecoder> noopServiceStatusHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, ServiceStatusSbeDecoder payload) ->{};
	private final Handler<CommandSbeDecoder> noopCommandHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandSbeDecoder payload) ->{};
	private final Handler<CommandAckSbeDecoder> noopCommandAckHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, CommandAckSbeDecoder payload) ->{};
	private final Handler<PingSbeDecoder> noopPingHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, PingSbeDecoder payload) ->{};
	private final Handler<EchoSbeDecoder> noopEchoHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, EchoSbeDecoder payload) ->{};
	private final Handler<RiskStateSbeDecoder> noopRiskStateHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskStateSbeDecoder payload) ->{};
	private final Handler<RiskControlSbeDecoder> noopRiskControlHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, RiskControlSbeDecoder payload) ->{};
	private final Handler<PositionSbeDecoder> noopPositionHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, PositionSbeDecoder payload) ->{};
	private final Handler<EventSbeDecoder> noopEventHandler = (DirectBuffer buffer, int offset, MessageHeaderDecoder header, EventSbeDecoder payload) ->{};
	
	public JournalPrinter(int numUserSupplied){
		this.header = (s) -> {};
		this.userSupplied = new Object[numUserSupplied];
		this.outputStream = System.out;
		this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(ServiceConstant.MAX_MESSAGE_SIZE));
		this.decoders = new Decoder[ServiceConstant.MAX_TEMPLATE_ID];
		for (int i = 0; i < decoders.length; i++){
			decoders[i] = Decoder.NULL_HANDLER;
		}
		
		this.decoders[CommandSbeDecoder.TEMPLATE_ID] = commandDecoder;
		this.decoders[CommandAckSbeDecoder.TEMPLATE_ID] = commandAckDecoder;
		this.decoders[EchoSbeDecoder.TEMPLATE_ID] = echoDecoder;
		this.decoders[FragmentSbeDecoder.TEMPLATE_ID] = fragmentDecoder;
		this.decoders[FragmentInitSbeDecoder.TEMPLATE_ID] = fragmentInitDecoder;
		this.decoders[AggregateOrderBookUpdateSbeDecoder.TEMPLATE_ID] = aggregateOrderBookUpdateDecoder;
		this.decoders[OrderBookSnapshotSbeDecoder.TEMPLATE_ID] = orderBookSnapshotDecoder;
		this.decoders[AmendOrderRequestSbeDecoder.TEMPLATE_ID] = amendOrderRequestDecoder;
		this.decoders[NewOrderRequestSbeDecoder.TEMPLATE_ID] = newOrderRequestDecoder;
		this.decoders[CancelOrderRequestSbeDecoder.TEMPLATE_ID] = cancelOrderRequestDecoder;

		// Order and trade related
		this.decoders[OrderCompletionSbeDecoder.TEMPLATE_ID] = orderCompletionDecoder;
		this.decoders[OrderRequestCompletionSbeDecoder.TEMPLATE_ID] = orderRequestCompletionDecoder;
		this.decoders[OrderRequestAcceptedSbeDecoder.TEMPLATE_ID] = orderRequestAcceptedDecoder;
		
		this.decoders[PingSbeDecoder.TEMPLATE_ID] = pingDecoder;
		this.decoders[PositionSbeDecoder.TEMPLATE_ID] = positionDecoder;
		this.decoders[RiskControlSbeDecoder.TEMPLATE_ID] = riskControlDecoder;
		this.decoders[RiskStateSbeDecoder.TEMPLATE_ID] = riskStateDecoder;
		this.decoders[RequestSbeDecoder.TEMPLATE_ID] = requestDecoder;
		this.decoders[ServiceStatusSbeDecoder.TEMPLATE_ID] = serviceStatusDecoder;
		this.decoders[TimerEventSbeDecoder.TEMPLATE_ID] = timerEventDecoder;
		this.decoders[MarketDataTradeSbeDecoder.TEMPLATE_ID] = marketDataTradeDecoder;
		this.decoders[BoobsSbeDecoder.TEMPLATE_ID] = boobsDecoder;
		this.decoders[MarketStatusSbeDecoder.TEMPLATE_ID] = marketStatusDecoder;
		this.decoders[ExchangeSbeDecoder.TEMPLATE_ID] = exchangeDecoder;
		this.decoders[IssuerSbeDecoder.TEMPLATE_ID] = issuerDecoder;
		this.decoders[SecuritySbeDecoder.TEMPLATE_ID] = securityDecoder;
		this.decoders[StrategyTypeSbeDecoder.TEMPLATE_ID] = stratTypeDecoder;
        this.decoders[StrategySwitchSbeDecoder.TEMPLATE_ID] = strategySwitchDecoder;
        this.decoders[StrategyParamsSbeDecoder.TEMPLATE_ID] = strategyParamsDecoder;
        this.decoders[StrategyUndParamsSbeDecoder.TEMPLATE_ID] = strategyUndParamsDecoder;
        this.decoders[StrategyWrtParamsSbeDecoder.TEMPLATE_ID] = strategyWrtParamsDecoder;
        this.decoders[StrategyIssuerParamsSbeDecoder.TEMPLATE_ID] = strategyIssuerParamsDecoder;
        this.decoders[GenericTrackerSbeDecoder.TEMPLATE_ID] = genericTrackerDecoder;
        this.decoders[EventSbeDecoder.TEMPLATE_ID] = eventDecoder;
        this.decoders[DividendCurveSbeDecoder.TEMPLATE_ID] = dividendCurveDecoder;
        this.decoders[GreeksSbeDecoder.TEMPLATE_ID] = greeksDecoder;
        this.decoders[MarketStatsSbeDecoder.TEMPLATE_ID] = marketStatsDecoder;
		this.entityDecoderMgr = EntityDecoderManager.of(this.decoders);
		this.responseDecoder = ResponseDecoder.of(this.entityDecoderMgr);
		this.decoders[ResponseSbeDecoder.TEMPLATE_ID] = this.responseDecoder;
		
		aggregateOrderBookUpdateDecoder.handlerList().add(noopAggregateOrderBookUpdateHandler);
		orderBookSnapshotDecoder.handlerList().add(noopOrderBookSnapshotHandler);
		newOrderRequestDecoder.handlerList().add(noopNewOrderRequestHandler);
		amendOrderRequestDecoder.handlerList().add(noopAmendOrderRequestHandler);
		cancelOrderRequestDecoder.handlerList().add(noopCancelOrderRequestHandler);
		orderCompletionDecoder.handlerList().add(noopOrderCompletionHandler);
		orderRequestCompletionDecoder.handlerList().add(noopOrderRequestCompletionHandler);
		orderRequestAcceptedDecoder.handlerList().add(noopOrderRequestAcceptedHandler);
		timerEventDecoder.handlerList().add(noopTimerEventHandler);
		marketDataTradeDecoder.handlerList().add(noopMarketDataTradeHandler);
		boobsDecoder.handlerList().add(noopBoobsHandler);
		marketStatusDecoder.handlerList().add(noopMarketStatusHandler);
		exchangeDecoder.handlerList().add(noopExchangeHandler);
		issuerDecoder.handlerList().add(noopIssuerHandler);
		securityDecoder.registerHandler(noopSecurityHandler);
		stratTypeDecoder.handlerList().add(noopStrategyTypeHandler);
	    strategySwitchDecoder.handlerList().add(noopStrategySwitchHandler);
	    strategyParamsDecoder.handlerList().add(noopStrategyParamsHandler);
	    strategyUndParamsDecoder.handlerList().add(noopStrategyUndParamsHandler);
	    strategyWrtParamsDecoder.handlerList().add(noopStrategyWrtParamsHandler);
	    strategyIssuerParamsDecoder.handlerList().add(noopStrategyIssuerParamsHandler);
	    genericTrackerDecoder.handlerList().add(noopGenericTrackerHandler);
	    dividendCurveDecoder.handlerList().add(noopDividendCurveHandler);
	    greeksDecoder.handlerList().add(noopGreeksHandler);
	    marketStatsDecoder.handlerList().add(noopMarketStatsHandler);

	    // Orders
		orderAcceptedDecoder.handlerList().add(noopOrderAcceptedHandler);
		orderAcceptedWithOrderInfoDecoder.handlerList().add(noopOrderAcceptedWithOrderInfoHandler);
		orderCancelledDecoder.handlerList().add(noopOrderCancelledHandler);
		orderCancelledWithOrderInfoDecoder.handlerList().add(noopOrderCancelledWithOrderInfoHandler);
		orderExpiredDecoder.handlerList().add(noopOrderExpiredHandler);
		orderExpiredWithOrderInfoDecoder.handlerList().add(noopOrderExpiredWithOrderInfoHandler);
		orderRejectedDecoder.handlerList().add(noopOrderRejectedHandler);
		orderRejectedWithOrderInfoDecoder.handlerList().add(noopOrderRejectedWithOrderInfoHandler);
		tradeCreatedDecoder.handlerList().add(noopTradeCreatedHandler);
		tradeCreatedWithOrderInfoDecoder.handlerList().add(noopTradeCreatedWithOrderInfoHandler); 
		tradeCancelledDecoder.handlerList().add(noopTradeCancelledHandler);
		orderDecoder.registerHandler(noopOrderHandler);
		tradeDecoder.handlerList().add(noopTradeHandler);
		orderCancelRejectedDecoder.handlerList().add(noopOrderCancelRejectedHandler);
		orderCancelRejectedWithOrderInfoDecoder.handlerList().add(noopOrderCancelRejectedWithOrderInfoHandler);
		orderAmendedDecoder.handlerList().add(noopOrderAmendedHandler);
		orderAmendRejectedDecoder.handlerList().add(noopOrderAmendRejectedHandler);
		
		fragmentDecoder.handlerList().add(noopFragmentHandler);
		fragmentInitDecoder.handlerList().add(noopFragmentInitHandler);
		requestDecoder.handlerList().add(noopRequestHandler);
		responseDecoder.handlerList().add(noopResponseHandler);
		serviceStatusDecoder.handlerList().add(noopServiceStatusHandler);
		commandDecoder.handlerList().add(noopCommandHandler);
		commandAckDecoder.handlerList().add(noopCommandAckHandler);
		pingDecoder.handlerList().add(noopPingHandler);
		echoDecoder.handlerList().add(noopEchoHandler);
		riskStateDecoder.handlerList().add(noopRiskStateHandler);
		riskControlDecoder.handlerList().add(noopRiskControlHandler);
		positionDecoder.handlerList().add(noopPositionHandler);
		eventDecoder.handlerList().add(noopEventHandler);
	}

	
	public JournalPrinter outputStream(PrintStream outputStream){
		this.outputStream = outputStream;
		return this;
	}
	
	public JournalPrinter headerWriter(Consumer<PrintStream> writer){
		this.header = writer;
		return this;
	}
	
	public JournalPrinter commandHandler(Handler<CommandSbeDecoder> formatter){
		this.commandDecoder.handlerList().remove(noopCommandHandler);
		this.commandDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter commandAckHandler(Handler<CommandAckSbeDecoder> formatter){
		this.commandAckDecoder.handlerList().remove(noopCommandAckHandler);
		this.commandAckDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter echoFormatter(Handler<EchoSbeDecoder> formatter){
		this.echoDecoder.handlerList().remove(noopEchoHandler);
		this.echoDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter eventFormatter(Handler<EventSbeDecoder> formatter){
		this.eventDecoder.handlerList().remove(noopEventHandler);
		this.eventDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter aggregateOrderBookUpdateFormatter(Handler<AggregateOrderBookUpdateSbeDecoder> formatter){
		this.aggregateOrderBookUpdateDecoder.handlerList().remove(noopAggregateOrderBookUpdateHandler);
		this.aggregateOrderBookUpdateDecoder.handlerList().add(formatter);
		return this;
	}
	
	public JournalPrinter orderBookSnapshotFormatter(Handler<OrderBookSnapshotSbeDecoder> formatter){
		orderBookSnapshotDecoder.handlerList().remove(noopOrderBookSnapshotHandler);
		orderBookSnapshotDecoder.handlerList().add(formatter);
		return this;
	}
	
	public JournalPrinter amendOrderRequestFormatter(Handler<AmendOrderRequestSbeDecoder> formatter){
		amendOrderRequestDecoder.handlerList().remove(noopAmendOrderRequestHandler);
		amendOrderRequestDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter marketDataTradeFormatter(Handler<MarketDataTradeSbeDecoder> formatter){
		marketDataTradeDecoder.handlerList().remove(noopMarketDataTradeHandler);
		marketDataTradeDecoder.handlerList().add(formatter);
		return this;
	}
	
	public JournalPrinter cancelOrderRequestFormatter(Handler<CancelOrderRequestSbeDecoder> formatter){
		cancelOrderRequestDecoder.handlerList().remove(noopCancelOrderRequestHandler);
		cancelOrderRequestDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderCompletionFormatter(Handler<OrderCompletionSbeDecoder> formatter){
		orderCompletionDecoder.handlerList().remove(noopOrderCompletionHandler);
		orderCompletionDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter newOrderRequestFormatter(Handler<NewOrderRequestSbeDecoder> formatter){
		newOrderRequestDecoder.handlerList().remove(noopNewOrderRequestHandler);
		newOrderRequestDecoder.handlerList().add(formatter);
		return this;
	}
	
	public JournalPrinter orderFormatter(Handler<OrderSbeDecoder> formatter){
		orderDecoder.unregisterHandler(noopOrderHandler);
		orderDecoder.registerHandler(formatter);
		return this;
	}
	
	public JournalPrinter orderAcceptedFormatter(Handler<OrderAcceptedSbeDecoder> formatter){
		orderAcceptedDecoder.handlerList().remove(noopOrderAcceptedHandler);
		orderAcceptedDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderAcceptedWithOrderInfoFormatter(Handler<OrderAcceptedWithOrderInfoSbeDecoder> formatter){
		orderAcceptedWithOrderInfoDecoder.handlerList().remove(noopOrderAcceptedWithOrderInfoHandler);
		orderAcceptedWithOrderInfoDecoder.handlerList().add(formatter);
		return this;
	}
	
	public JournalPrinter orderCancelledFormatter(Handler<OrderCancelledSbeDecoder> formatter){
		orderCancelledDecoder.handlerList().remove(noopOrderCancelledHandler);
		orderCancelledDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderCancelledWithOrderInfoFormatter(Handler<OrderCancelledWithOrderInfoSbeDecoder> formatter){
		orderCancelledWithOrderInfoDecoder.handlerList().remove(noopOrderCancelledWithOrderInfoHandler);
		orderCancelledWithOrderInfoDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderCancelRejectedFormatter(Handler<OrderCancelRejectedSbeDecoder> formatter){
		orderCancelRejectedDecoder.handlerList().remove(noopOrderCancelRejectedHandler);
		orderCancelRejectedDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderCancelRejectedWithOrderInfoFormatter(Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> formatter){
		orderCancelRejectedWithOrderInfoDecoder.handlerList().remove(noopOrderCancelRejectedWithOrderInfoHandler);
		orderCancelRejectedWithOrderInfoDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderExpiredFormatter(Handler<OrderExpiredSbeDecoder> formatter){
		orderExpiredDecoder.handlerList().remove(noopOrderExpiredHandler);
		orderExpiredDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderExpiredWithOrderInfoFormatter(Handler<OrderExpiredWithOrderInfoSbeDecoder> formatter){
		orderExpiredWithOrderInfoDecoder.handlerList().remove(noopOrderExpiredWithOrderInfoHandler);
		orderExpiredWithOrderInfoDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderAmendedFormatter(Handler<OrderAmendedSbeDecoder> formatter){
		orderAmendedDecoder.handlerList().remove(noopOrderAmendedHandler);
		orderAmendedDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderAmendRejectedFormatter(Handler<OrderAmendRejectedSbeDecoder> formatter){
		orderAmendRejectedDecoder.handlerList().remove(noopOrderAmendRejectedHandler);
		orderAmendRejectedDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderRejectedFormatter(Handler<OrderRejectedSbeDecoder> formatter){
		orderRejectedDecoder.handlerList().remove(noopOrderRejectedHandler);
		orderRejectedDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderRejectedWithOrderInfoFormatter(Handler<OrderRejectedWithOrderInfoSbeDecoder> formatter){
		orderRejectedWithOrderInfoDecoder.handlerList().remove(noopOrderRejectedWithOrderInfoHandler);
		orderRejectedWithOrderInfoDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderRequestCompletionFormatter(Handler<OrderRequestCompletionSbeDecoder> formatter){
		orderRequestCompletionDecoder.handlerList().remove(noopOrderRequestCompletionHandler);
		orderRequestCompletionDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter orderRequestAcceptedFormatter(Handler<OrderRequestAcceptedSbeDecoder> formatter){
		orderRequestAcceptedDecoder.handlerList().remove(noopOrderRequestAcceptedHandler);
		orderRequestAcceptedDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter pingFormatter(Handler<PingSbeDecoder> formatter){
		pingDecoder.handlerList().remove(noopPingHandler);
		pingDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter positionFormatter(Handler<PositionSbeDecoder> formatter){
		positionDecoder.handlerList().remove(noopPositionHandler);
		positionDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter requestFormatter(Handler<RequestSbeDecoder> formatter){
		requestDecoder.handlerList().remove(noopRequestHandler);
		requestDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter responseFormatter(Handler<ResponseSbeDecoder> formatter){
		responseDecoder.handlerList().remove(noopResponseHandler);
		responseDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter riskControlFormatter(Handler<RiskControlSbeDecoder> formatter){
		riskControlDecoder.handlerList().remove(noopRiskControlHandler);
		riskControlDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter riskStateFormatter(Handler<RiskStateSbeDecoder> formatter){
		riskStateDecoder.handlerList().remove(noopRiskStateHandler);
		riskStateDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter securityFormatter(Handler<SecuritySbeDecoder> formatter){
		securityDecoder.unregisterHandler(noopSecurityHandler);
		securityDecoder.registerHandler(formatter);
		return this;
	}
	
	public JournalPrinter strategyTypeFormatter(Handler<StrategyTypeSbeDecoder> formatter){
		stratTypeDecoder.handlerList().remove(noopStrategyTypeHandler);
		stratTypeDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter serviceStatusFormatter(Handler<ServiceStatusSbeDecoder> formatter){
		serviceStatusDecoder.handlerList().remove(noopServiceStatusHandler);
		serviceStatusDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter timerEventFormatter(Handler<TimerEventSbeDecoder> formatter){
		timerEventDecoder.handlerList().remove(noopTimerEventHandler);
		timerEventDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter tradeFormatter(Handler<TradeSbeDecoder> formatter){
		tradeDecoder.handlerList().remove(noopTradeHandler);
		tradeDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter tradeCreatedFormatter(Handler<TradeCreatedSbeDecoder> formatter){
		tradeCreatedDecoder.handlerList().remove(noopTradeCreatedHandler);
		tradeCreatedDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter tradeCreatedWithOrderInfoFormatter(Handler<TradeCreatedWithOrderInfoSbeDecoder> formatter){
		tradeCreatedWithOrderInfoDecoder.handlerList().remove(noopTradeCreatedWithOrderInfoHandler);
		tradeCreatedWithOrderInfoDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter tradeCancelledFormatter(Handler<TradeCancelledSbeDecoder> formatter){
		tradeCancelledDecoder.handlerList().remove(noopTradeCancelledHandler);
		tradeCancelledDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter exchangeFormatter(Handler<ExchangeSbeDecoder> formatter){
		exchangeDecoder.handlerList().remove(noopExchangeHandler);
		exchangeDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter issuerFormatter(Handler<IssuerSbeDecoder> formatter){
		issuerDecoder.handlerList().remove(noopIssuerHandler);
		issuerDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter strategySwitchFormatter(Handler<StrategySwitchSbeDecoder> formatter){
		strategySwitchDecoder.handlerList().remove(noopStrategySwitchHandler);
		strategySwitchDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter strategyParamsFormatter(Handler<StrategyParamsSbeDecoder> formatter){
		strategyParamsDecoder.handlerList().remove(noopStrategyParamsHandler);
		strategyParamsDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter strategyUndParamsFormatter(Handler<StrategyUndParamsSbeDecoder> formatter){
		strategyUndParamsDecoder.handlerList().remove(noopStrategyUndParamsHandler);
		strategyUndParamsDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter strategyWrtParamsFormatter(Handler<StrategyWrtParamsSbeDecoder> formatter){
		strategyWrtParamsDecoder.handlerList().remove(noopStrategyWrtParamsHandler);
		strategyWrtParamsDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter strategyIssuerParamsFormatter(Handler<StrategyIssuerParamsSbeDecoder> formatter){
		strategyIssuerParamsDecoder.handlerList().remove(noopStrategyIssuerParamsHandler);
		strategyIssuerParamsDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter boobsFormatter(Handler<BoobsSbeDecoder> formatter){
		boobsDecoder.handlerList().remove(noopBoobsHandler);
		boobsDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter marketStatusFormatter(Handler<MarketStatusSbeDecoder> formatter){
		marketStatusDecoder.handlerList().remove(noopMarketStatusHandler);
		marketStatusDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter genericTrackerFormatter(Handler<GenericTrackerSbeDecoder> formatter){
		genericTrackerDecoder.handlerList().remove(noopGenericTrackerHandler);
		genericTrackerDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter dividendCurveFormatter(Handler<DividendCurveSbeDecoder> formatter){
		dividendCurveDecoder.handlerList().remove(noopDividendCurveHandler);
		dividendCurveDecoder.handlerList().add(formatter);
		return this;
	}

	public JournalPrinter greeksFormatter(Handler<GreeksSbeDecoder> formatter){
		greeksDecoder.handlerList().remove(noopGreeksHandler);
		greeksDecoder.handlerList().add(formatter);
		return this;
	}
	
	public JournalPrinter marketStatsFormatter(Handler<MarketStatsSbeDecoder> formatter){
		marketStatsDecoder.handlerList().remove(noopMarketStatsHandler);
		marketStatsDecoder.handlerList().add(formatter);
		return this;
	}

	public void output(JournalReader reader, int maxNumRows) throws Exception {
		LOG.info("Start processing journal");
		
		reader.open(buffer, 0, ServiceConstant.MAX_MESSAGE_SIZE);
		
		com.lunar.message.io.sbe.MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
		JournalRecordSbeDecoder recordDecoder = new JournalRecordSbeDecoder();
		
		header.accept(outputStream);
		
		int i = 0;
		while (reader.read(recordDecoder, buffer, 0, ServiceConstant.MAX_MESSAGE_SIZE) && i < maxNumRows){
			// Decode with journal header
			messageHeader.wrap(buffer, 0);
			int readTemplateId = messageHeader.templateId();
//			LOG.info("reading line: {}", MessageReceiver.templateName(readTemplateId));
			
			if (decoders[readTemplateId].output(outputStream, userSupplied, recordDecoder, buffer, 0, messageHeader)){
				i++;
			}
		}
		
		reader.close();
		LOG.info("Done processing journal");
	}
	
	public JournalPrinter addUserSupplied(int index, Object value){
		userSupplied[index] = value;
		return this;
	}
	
	public Object getUserSupplied(int index){
		return userSupplied[index];
	}
}
