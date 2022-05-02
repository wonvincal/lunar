package com.lunar.message.binary;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.Message;
import com.lunar.message.binary.FragmentAssembler.FragmentExceptionHandler;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AmendOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.BoobsSbeDecoder;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
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
import com.lunar.message.io.sbe.NewCompositeOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
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
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.order.OrderChannelBuffer;
import com.lunar.service.ServiceConstant;

/**
 * Message receiving logics.  This class does the following:
 * <p>
 * <ol>
 * <li>Fragment handling per sender
 * <li>Decoding.  It is really mainly being used by matching template id to a sbe.  This matching of template id implies that
 *    we cannot inline the method.  If we have a specific type of message that we want to decode in tight loop, better use
 *    a specific sbe for that.  We need to put the most common message types together: 1) have id close to each other,
 *    2) put them beside each other in this class
 * </li>
 * </ol>
 * <p>
 * Some caching and performance related considerations:
 * <ul>
 * <li>Similar decoders should be placed beside each other</li>
 * <li>Create an array of all possible message types, so that we can use reference a decoder directly with a message's template id</li>
 * <li>Mapping template id to a particular decoder implies that it is impossible to do method inlining</li>
 * </ul>
 * <strong>TODO</strong> If there is a particular service that needs only a couple of decoders, we should create a specific MessageDecoder with
 * just those decoders, so that we can make better use of cache.
 * <p>
 * @author wongca
 *
 */
public final class MessageReceiver {
	static final Logger LOG = LogManager.getLogger(MessageReceiver.class);
	private final byte[] byteArrayBuffer = new byte[ServiceConstant.DEFAULT_RECEIVER_BUFFER_SIZE];
	private final Decoder[] decoders;
	private final MessageHeaderDecoder messageHeader;
	
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
	private final FragmentAssembler fragmentAssembler;
	private final OrderChannelBuffer orderChannelBuffer;
	private final ExchangeDecoder exchangeDecoder = ExchangeDecoder.of();
	private final IssuerDecoder issuerDecoder = IssuerDecoder.of();
	private final SecurityDecoder securityDecoder = SecurityDecoder.of();
	private final StrategyTypeDecoder stratTypeDecoder = StrategyTypeDecoder.of();
    private final StrategySwitchDecoder strategySwitchDecoder = StrategySwitchDecoder.of();
    private final StrategyParamsDecoder strategyParamsDecoder = StrategyParamsDecoder.of();
    private final StrategyUndParamsDecoder strategyUndParamsDecoder = StrategyUndParamsDecoder.of();
    private final StrategyWrtParamsDecoder strategyWrtParamsDecoder = StrategyWrtParamsDecoder.of();
    private final StrategyIssuerParamsDecoder strategyIssuerParamsDecoder = StrategyIssuerParamsDecoder.of();
    private final StrategyIssuerUndParamsDecoder strategyIssuerUndParamsDecoder = StrategyIssuerUndParamsDecoder.of();
    private final GenericTrackerDecoder genericTrackerDecoder = GenericTrackerDecoder.of();
    private final DividendCurveDecoder dividendCurveDecoder = DividendCurveDecoder.of();
    private final GreeksDecoder greeksDecoder = GreeksDecoder.of();
    private final MarketStatsDecoder marketStatsDecoder = MarketStatsDecoder.of();
    private final ScoreBoardSchemaDecoder scoreBoardSchemaDecoder = ScoreBoardSchemaDecoder.of();
    private final ScoreBoardDecoder scoreBoardDecoder = ScoreBoardDecoder.of();
    private final NewCompositeOrderRequestDecoder newCompositeOrderRequestDecoder = NewCompositeOrderRequestDecoder.of();

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
	private final NoteDecoder noteDecoder = NoteDecoder.of();
	private final ChartDataDecoder chartDataDecoder = ChartDataDecoder.of();
	
	// entity decoder
	private final EntityDecoderManager entityDecoderMgr;
	
	static {
		
	}
	
	public static MessageReceiver of(int maxNumSinks, OrderChannelBuffer orderChannelBuffer){
		return new MessageReceiver(maxNumSinks, orderChannelBuffer);
	}
	
	public static MessageReceiver of(OrderChannelBuffer orderChannelBuffer){
		return new MessageReceiver(ServiceConstant.DEFAULT_MAX_NUM_SINKS, orderChannelBuffer);
	}

	public static MessageReceiver of(){
		return new MessageReceiver(ServiceConstant.DEFAULT_MAX_NUM_SINKS, 
				OrderChannelBuffer.of(ServiceConstant.ORDER_BUFFER_MESSAGE_BUFFER_CAPACITY, 
				ServiceConstant.ORDER_BUFFER_SNAPSHOT_BUFFER_CAPACITY));
	}

	private final static String[] TEMPLATE_NAMES;
	
	static {
		TEMPLATE_NAMES = new String[ServiceConstant.MAX_TEMPLATE_ID];
		TEMPLATE_NAMES[CommandSbeDecoder.TEMPLATE_ID] = "CommandSbe";
		TEMPLATE_NAMES[CommandAckSbeDecoder.TEMPLATE_ID] = "CommandAckSbe";
		TEMPLATE_NAMES[EchoSbeDecoder.TEMPLATE_ID] = "EchoSbeDecoder";
		TEMPLATE_NAMES[FragmentSbeDecoder.TEMPLATE_ID] = "FragmentSbeDecoder";
		TEMPLATE_NAMES[FragmentInitSbeDecoder.TEMPLATE_ID] = "FragmentInitSbeDecoder";
		TEMPLATE_NAMES[AggregateOrderBookUpdateSbeDecoder.TEMPLATE_ID] = "AggregateOrderBookUpdateSbe";
		TEMPLATE_NAMES[OrderBookSnapshotSbeDecoder.TEMPLATE_ID] = "OrderBookSnapshotSbe";
		TEMPLATE_NAMES[AmendOrderRequestSbeDecoder.TEMPLATE_ID] = "AmendOrderRequestSbe";
		TEMPLATE_NAMES[NewOrderRequestSbeDecoder.TEMPLATE_ID] = "NewOrderRequestSbe";
		TEMPLATE_NAMES[CancelOrderRequestSbeDecoder.TEMPLATE_ID] = "CancelOrderRequestSbe";
		TEMPLATE_NAMES[OrderCompletionSbeDecoder.TEMPLATE_ID] = "OrderCompletionSbe";
		TEMPLATE_NAMES[OrderRequestCompletionSbeDecoder.TEMPLATE_ID] = "OrderRequestCompletionSbe";
		TEMPLATE_NAMES[OrderRequestAcceptedSbeDecoder.TEMPLATE_ID] = "OrderRequestAcceptedSbe";
		
		TEMPLATE_NAMES[PingSbeDecoder.TEMPLATE_ID] = "PingSbe";
		TEMPLATE_NAMES[PositionSbeDecoder.TEMPLATE_ID] = "PositionSbe";
		TEMPLATE_NAMES[RiskControlSbeDecoder.TEMPLATE_ID] = "RiskControlSbe";
		TEMPLATE_NAMES[RiskStateSbeDecoder.TEMPLATE_ID] = "RiskStateSbe";
		TEMPLATE_NAMES[RequestSbeDecoder.TEMPLATE_ID] = "RequestSbe";
		TEMPLATE_NAMES[ServiceStatusSbeDecoder.TEMPLATE_ID] = "ServiceStatusSbe";
		TEMPLATE_NAMES[TimerEventSbeDecoder.TEMPLATE_ID] = "TimerEventSbe";
		TEMPLATE_NAMES[MarketDataTradeSbeDecoder.TEMPLATE_ID] = "MarketDataTradeSbe";
		TEMPLATE_NAMES[BoobsSbeDecoder.TEMPLATE_ID] = "BoobsSbe";
		TEMPLATE_NAMES[MarketStatusSbeDecoder.TEMPLATE_ID] = "MarketStatusSbe";
		TEMPLATE_NAMES[ExchangeSbeDecoder.TEMPLATE_ID] = "ExchangeSbe";
		TEMPLATE_NAMES[IssuerSbeDecoder.TEMPLATE_ID] = "IssuerSbe";
		TEMPLATE_NAMES[SecuritySbeDecoder.TEMPLATE_ID] = "SecuritySbe";
		TEMPLATE_NAMES[StrategyTypeSbeDecoder.TEMPLATE_ID] = "StrategyTypeSbe";
        TEMPLATE_NAMES[StrategySwitchSbeDecoder.TEMPLATE_ID] = "StrategySwitchSbe";
        TEMPLATE_NAMES[StrategyParamsSbeDecoder.TEMPLATE_ID] = "StrategyParamsSbe";
        TEMPLATE_NAMES[StrategyUndParamsSbeDecoder.TEMPLATE_ID] = "StrategyUndParamsSbe";
        TEMPLATE_NAMES[StrategyWrtParamsSbeDecoder.TEMPLATE_ID] = "StrategyWrtParamsSbe";
        TEMPLATE_NAMES[StrategyIssuerParamsSbeDecoder.TEMPLATE_ID] = "StrategyIssuerParamsSbe";
        TEMPLATE_NAMES[StrategyIssuerUndParamsSbeDecoder.TEMPLATE_ID] = "StrategyIssuerUndParamsSbe";
        TEMPLATE_NAMES[GenericTrackerSbeDecoder.TEMPLATE_ID] = "GenericTrackerSbe";
        TEMPLATE_NAMES[EventSbeDecoder.TEMPLATE_ID] = "EventSbe";
        TEMPLATE_NAMES[DividendCurveSbeDecoder.TEMPLATE_ID] = "DividendCurveSbe";
        TEMPLATE_NAMES[GreeksSbeDecoder.TEMPLATE_ID] = "GreeksSbe";
        TEMPLATE_NAMES[ResponseSbeDecoder.TEMPLATE_ID] = "ResponseSbe";
        TEMPLATE_NAMES[NoteSbeDecoder.TEMPLATE_ID] = "NoteSbe";
	}
	
	public static String templateName(int templateId){
		return TEMPLATE_NAMES[templateId];
	}
	
	MessageReceiver(int numSinks, OrderChannelBuffer orderChannelBuffer){
		this.messageHeader = new MessageHeaderDecoder();
		this.decoders = new Decoder[ServiceConstant.MAX_TEMPLATE_ID];
		for (int i = 0; i < decoders.length; i++){
			decoders[i] = Decoder.NULL_HANDLER;
		}
		this.orderChannelBuffer = orderChannelBuffer;
		this.orderChannelBuffer.register(this);
		
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
		this.decoders[NewCompositeOrderRequestSbeDecoder.TEMPLATE_ID] = newCompositeOrderRequestDecoder;

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
        this.decoders[StrategyIssuerUndParamsSbeDecoder.TEMPLATE_ID] = strategyIssuerUndParamsDecoder;
        this.decoders[GenericTrackerSbeDecoder.TEMPLATE_ID] = genericTrackerDecoder;
        this.decoders[EventSbeDecoder.TEMPLATE_ID] = eventDecoder;
        this.decoders[DividendCurveSbeDecoder.TEMPLATE_ID] = dividendCurveDecoder;
        this.decoders[GreeksSbeDecoder.TEMPLATE_ID] = greeksDecoder;
        this.decoders[MarketStatsSbeDecoder.TEMPLATE_ID] = marketStatsDecoder;
        this.decoders[ScoreBoardSchemaSbeDecoder.TEMPLATE_ID] = scoreBoardSchemaDecoder;
        this.decoders[ScoreBoardSbeDecoder.TEMPLATE_ID] = scoreBoardDecoder;
        this.decoders[NoteSbeDecoder.TEMPLATE_ID] = noteDecoder;
        this.decoders[ChartDataSbeDecoder.TEMPLATE_ID] = chartDataDecoder;

        // entity decode manager registers different entity type decoders 
		// in its constructor
		this.entityDecoderMgr = EntityDecoderManager.of(this.decoders);
		this.responseDecoder = ResponseDecoder.of(this.entityDecoderMgr);
		this.decoders[ResponseSbeDecoder.TEMPLATE_ID] = this.responseDecoder;
		this.fragmentAssembler = FragmentAssembler.of(numSinks, this);
	}
	
	void registerDecoder(int templateId, Decoder decoder){
		this.decoders[templateId] = decoder;
	}
	
	/**
	 * TODO change this into a more generic list of input parameters
	 * @param frame
	 * @return
	 */
	public int receive(final DirectBuffer buffer, int offset){
		// LOG.debug("received frame for decode: offset:{}, length:{}, bytes:{}", message.offset(), message.length(), LogUtil.dumpBinary(buffer.byteBuffer()));
		messageHeader.wrap(buffer, offset);
		final int templateId = messageHeader.templateId();
		// We should make sure our system does not produce fragments most of the time in order to make this branch predictable
		decoders[templateId].decode(buffer, offset, messageHeader);
		return messageHeader.encodedLength() + messageHeader.payloadLength();
	}
	
	public String dump(final DirectBuffer buffer, int offset){
        messageHeader.wrap(buffer, offset);
        final int version = messageHeader.version();
        final int blockLength = messageHeader.blockLength();
        final int templateId = messageHeader.templateId();
        final byte senderSinkId = messageHeader.senderSinkId();
        final byte dstSinkId = messageHeader.dstSinkId();
//        final int seq = messageHeader.seq();
	    try {
    		return String.format("dump: version:%d, blockLength:%d, templateId:%d, senderSinkId:%d, dstSinkId:%d %s", 
    				version, blockLength, templateId, senderSinkId, dstSinkId,
    				decoders[templateId].dump(buffer, offset, messageHeader));
	    }
	    catch (final Exception e) {
	        LOG.error("Error getting dump!", e);
            return String.format("dump: version:%d, blockLength:%d, templateId:%d, senderSinkId:%d, dstSinkId:%d %s", 
                    version, blockLength, templateId, senderSinkId, dstSinkId,
                    "Error getting dump"); 
	    }
	}

	public Message decodeAsMessage(final DirectBuffer buffer, int offset){
		// Look for template id
		messageHeader.wrap(buffer, offset);
//		final int version = messageHeader.version();
//		final int blockLength = messageHeader.blockLength();
		final int templateId = messageHeader.templateId();
//		final byte senderSinkId = messageHeader.senderSinkId();
//		final byte dstSinkId = messageHeader.dstSinkId();
//		final int seq = messageHeader.seq();
		// LOG.debug("received msg for decode: templateId:{}, version:{}, blockLength:{}", templateId, version, blockLength);
		return decoders[templateId].decodeAsMessage(buffer, offset, messageHeader);
	}
	
	public void startOrderChannelBuffer(){
		this.orderChannelBuffer.start();
	}
	
	public void stopOrderChannelBuffer(){
		this.orderChannelBuffer.stop();
	}
	
	/**
	 * TODO remove this!
	 * @return
	 */
	public byte[] byteArrayBuffer(){
		return this.byteArrayBuffer;
	}
	
	public HandlerList<CommandSbeDecoder> commandHandlerList(){
		return this.commandDecoder.handlerList();
	}

	public HandlerList<CommandAckSbeDecoder> commandAckHandlerList(){
		return this.commandAckDecoder.handlerList();
	}

	public HandlerList<EchoSbeDecoder> echoHandlerList(){
		return this.echoDecoder.handlerList();
	}

	public HandlerList<EventSbeDecoder> eventHandlerList(){
		return this.eventDecoder.handlerList();
	}

	public HandlerList<FragmentSbeDecoder> fragmentHandlerList(){
		return this.fragmentDecoder.handlerList();
	}

	public HandlerList<FragmentInitSbeDecoder> fragmentInitHandlerList(){
		return this.fragmentInitDecoder.handlerList();
	}

	public HandlerList<AggregateOrderBookUpdateSbeDecoder> aggregateOrderBookUpdateHandlerList(){
		return this.aggregateOrderBookUpdateDecoder.handlerList();
	}
	
	public HandlerList<OrderBookSnapshotSbeDecoder> orderBookSnapshotHandlerList() {
		return this.orderBookSnapshotDecoder.handlerList();
	}

	public HandlerList<AmendOrderRequestSbeDecoder> amendOrderRequestHandlerList(){
		return this.amendOrderRequestDecoder.handlerList();
    }

	public HandlerList<MarketDataTradeSbeDecoder> marketDataTradeHandlerList(){
		return this.marketDataTradeDecoder.handlerList();
	}
	
	public HandlerList<CancelOrderRequestSbeDecoder> cancelOrderRequestHandlerList(){
		return this.cancelOrderRequestDecoder.handlerList();
	}

	public HandlerList<OrderCompletionSbeDecoder> orderCompletionHandlerList(){
		return this.orderCompletionDecoder.handlerList();
	}

	public HandlerList<NewOrderRequestSbeDecoder> newOrderRequestHandlerList(){
		return this.newOrderRequestDecoder.handlerList();
	}

	public HandlerList<NewCompositeOrderRequestSbeDecoder> newCompositeOrderRequestHandlerList(){
		return this.newCompositeOrderRequestDecoder.handlerList();
	}
	
	public HandlerList<OrderSbeDecoder> orderHandlerList(){
		return this.orderChannelBuffer.supplier().orderHandlerList();
	}

	public HandlerList<OrderAcceptedSbeDecoder> orderAcceptedHandlerList(){
		return this.orderChannelBuffer.supplier().orderAcceptedHandlerList();
	}

	public HandlerList<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandlerList(){
		return this.orderChannelBuffer.supplier().orderAcceptedWithOrderInfoHandlerList();
	}

	public HandlerList<OrderCancelledSbeDecoder> orderCancelledHandlerList(){
		return this.orderChannelBuffer.supplier().orderCancelledHandlerList();
	}

	public HandlerList<OrderCancelledWithOrderInfoSbeDecoder> orderCancelledWithOrderInfoHandlerList(){
		return this.orderChannelBuffer.supplier().orderCancelledWithOrderInfoHandlerList();
	}

	public HandlerList<OrderCancelRejectedSbeDecoder> orderCancelRejectedHandlerList(){
		return this.orderChannelBuffer.supplier().orderCancelRejectedHandlerList();
	}

	public HandlerList<OrderCancelRejectedWithOrderInfoSbeDecoder> orderCancelRejectedWithOrderInfoHandlerList(){
		return this.orderChannelBuffer.supplier().orderCancelRejectedWithOrderInfoHandlerList();
	}

	public HandlerList<OrderExpiredSbeDecoder> orderExpiredHandlerList(){
		return this.orderChannelBuffer.supplier().orderExpiredHandlerList();
	}

	public HandlerList<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoHandlerList(){
		return this.orderChannelBuffer.supplier().orderExpiredWithOrderInfoHandlerList();
	}

	public HandlerList<OrderAmendedSbeDecoder> orderAmendedHandlerList(){
		return this.orderChannelBuffer.supplier().orderAmendedHandlerList();
	}

	public HandlerList<OrderAmendRejectedSbeDecoder> orderAmendRejectedHandlerList(){
		return this.orderChannelBuffer.supplier().orderAmendRejectedHandlerList();
	}

	public HandlerList<OrderRejectedSbeDecoder> orderRejectedHandlerList(){
		return this.orderChannelBuffer.supplier().orderRejectedHandlerList();
	}

	public HandlerList<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoHandlerList(){
		return this.orderChannelBuffer.supplier().orderRejectedWithOrderInfoHandlerList();
	}

	public HandlerList<OrderRequestCompletionSbeDecoder> orderRequestCompletionHandlerList(){
		return this.orderRequestCompletionDecoder.handlerList();
	}

	public HandlerList<OrderRequestAcceptedSbeDecoder> orderRequestAcceptedHandlerList(){
		return this.orderRequestAcceptedDecoder.handlerList();
	}

	public HandlerList<PingSbeDecoder> pingHandlerList(){
		return this.pingDecoder.handlerList();
	}

	public HandlerList<PositionSbeDecoder> positionHandlerList(){
		return this.positionDecoder.handlerList();
	}

	public HandlerList<RequestSbeDecoder> requestHandlerList(){
		return this.requestDecoder.handlerList();
	}

	public HandlerList<ResponseSbeDecoder> responseHandlerList(){
		return this.responseDecoder.handlerList();
	}

	public HandlerList<RiskControlSbeDecoder> riskControlHandlerList(){
		return this.riskControlDecoder.handlerList();
	}

	public HandlerList<RiskStateSbeDecoder> riskStateHandlerList(){
		return this.riskStateDecoder.handlerList();
	}

	public HandlerList<SecuritySbeDecoder> securityHandlerList(){
		return this.securityDecoder.handlerList();
	}
	
	public HandlerList<StrategyTypeSbeDecoder> strategyTypeHandlerList() {
	    return this.stratTypeDecoder.handlerList();
	}

	public HandlerList<ServiceStatusSbeDecoder> serviceStatusHandlerList(){
		return this.serviceStatusDecoder.handlerList();
	}

	public HandlerList<TimerEventSbeDecoder> timerEventHandlerList(){
		return this.timerEventDecoder.handlerList();
	}

	public HandlerList<TradeSbeDecoder> tradeHandlerList(){
		return this.orderChannelBuffer.supplier().tradeHandlerList();
	}

	public HandlerList<TradeCreatedSbeDecoder> tradeCreatedHandlerList(){
		return this.orderChannelBuffer.supplier().tradeCreatedHandlerList();
	}

	public HandlerList<TradeCreatedWithOrderInfoSbeDecoder> tradeCreatedWithOrderInfoHandlerList(){
		return this.orderChannelBuffer.supplier().tradeCreatedWithOrderInfoHandlerList();
	}

	public HandlerList<TradeCancelledSbeDecoder> tradeCancelledHandlerList(){
		return this.orderChannelBuffer.supplier().tradeCancelledHandlerList();
	}

	public HandlerList<ExchangeSbeDecoder> exchangeHandlerList(){
		return this.exchangeDecoder.handlerList();
	}
	
	public HandlerList<IssuerSbeDecoder> issuerHandlerList() {
	    return this.issuerDecoder.handlerList();
	}
	
	public HandlerList<StrategySwitchSbeDecoder> strategySwitchHandlerList() {
	    return this.strategySwitchDecoder.handlerList();
	}
	
    public HandlerList<StrategyParamsSbeDecoder> strategyParamsHandlerList() {
        return this.strategyParamsDecoder.handlerList();
    }

    public HandlerList<StrategyUndParamsSbeDecoder> strategyUndParamsHandlerList() {
        return this.strategyUndParamsDecoder.handlerList();
    }

    public HandlerList<StrategyWrtParamsSbeDecoder> strategyWrtParamsHandlerList() {
        return this.strategyWrtParamsDecoder.handlerList();
    }
    
    public HandlerList<StrategyIssuerParamsSbeDecoder> strategyIssuerParamsHandlerList() {
        return this.strategyIssuerParamsDecoder.handlerList();
    }    
    
    public HandlerList<StrategyIssuerUndParamsSbeDecoder> strategyIssuerUndParamsHandlerList() {
        return this.strategyIssuerUndParamsDecoder.handlerList();
    }    

    public HandlerList<BoobsSbeDecoder> boobsHandlerList() {
    	return this.boobsDecoder.handlerList();
    }
    
    public HandlerList<MarketStatusSbeDecoder> marketStatusHandlerList() {
    	return this.marketStatusDecoder.handlerList();
    }
    
    public HandlerList<GenericTrackerSbeDecoder> genericTrackerHandlerList() {
        return this.genericTrackerDecoder.handlerList();
    }

    public HandlerList<DividendCurveSbeDecoder> dividendCurveHandlerList() {
        return this.dividendCurveDecoder.handlerList();
    }    
    
    public HandlerList<GreeksSbeDecoder> greeksHandlerList() {
        return this.greeksDecoder.handlerList();
    }    
    
    public HandlerList<MarketStatsSbeDecoder> marketStatsHandlerList() {
        return this.marketStatsDecoder.handlerList();
    }

    public HandlerList<ScoreBoardSchemaSbeDecoder> scoreBoardSchemaHandlerList() {
        return this.scoreBoardSchemaDecoder.handlerList();
    }
    
    public HandlerList<ScoreBoardSbeDecoder> scoreBoardHandlerList() {
        return this.scoreBoardDecoder.handlerList();
    }
    
	public HandlerList<NoteSbeDecoder> noteHandlerList(){
		return this.noteDecoder.handlerList();
	}

	public HandlerList<ChartDataSbeDecoder> chartDataHandlerList(){
		return this.chartDataDecoder.handlerList();
	}

	public int decodeDstSinkId(DirectBuffer buffer, int offset){
		messageHeader.wrap(buffer, offset);
		return messageHeader.dstSinkId();
	}
	
	public MessageReceiver fragmentAssemblerExceptionHandler(FragmentExceptionHandler exceptionHandler){
		this.fragmentAssembler.exceptionHandler(exceptionHandler);
		return this;
	}
	
	public RiskControlSbeDecoder extractRiskControlFromRequest(final DirectBuffer buffer, final int offset, int senderSinkId, RequestSbeDecoder request){
		byte templateId = request.parameterBinaryTemplateId();
		if (templateId == RequestSbeDecoder.parameterBinaryBlockLengthNullValue()){
			throw new IllegalArgumentException("No embedded message found in request");
		}
		TemplateType templateType = TemplateType.get(templateId);
		if (templateType != TemplateType.RISK_CONTROL){
			throw new IllegalArgumentException("Expect risk control template type in request [templateType:" + templateType.name() + "]");
		}
		int offsetToEntity = offset + MessageHeaderDecoder.ENCODED_LENGTH + RequestSbeDecoder.BLOCK_LENGTH + 
				RequestSbeDecoder.ParametersDecoder.sbeHeaderSize() + 
				request.parameters().count() * RequestSbeDecoder.ParametersDecoder.sbeBlockLength();
		
		return riskControlDecoder.sbe().wrap(buffer, 
				offsetToEntity, 
				request.parameterBinaryBlockLength(), 
				RiskControlSbeDecoder.SCHEMA_VERSION);
	}
}

