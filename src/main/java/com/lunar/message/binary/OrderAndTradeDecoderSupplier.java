package com.lunar.message.binary;

import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAcceptedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.TradeCancelledSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedSbeDecoder;
import com.lunar.message.io.sbe.TradeCreatedWithOrderInfoSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;

public class OrderAndTradeDecoderSupplier {
	private final OrderAcceptedDecoder orderAcceptedDecoder;
	private final OrderAcceptedWithOrderInfoDecoder orderAcceptedWithOrderInfoDecoder;
	private final OrderCancelledDecoder orderCancelledDecoder;
	private final OrderCancelledWithOrderInfoDecoder orderCancelledWithOrderInfoDecoder;
	private final OrderExpiredDecoder orderExpiredDecoder;
	private final OrderExpiredWithOrderInfoDecoder orderExpiredWithOrderInfoDecoder;
	private final OrderRejectedDecoder orderRejectedDecoder;
	private final OrderRejectedWithOrderInfoDecoder orderRejectedWithOrderInfoDecoder;
	private final TradeCreatedDecoder tradeCreatedDecoder;
	private final TradeCreatedWithOrderInfoDecoder tradeCreatedWithOrderInfoDecoder;
	private final TradeCancelledDecoder tradeCancelledDecoder;
	private final OrderCancelRejectedDecoder orderCancelRejectedDecoder;
	private final OrderCancelRejectedWithOrderInfoDecoder orderCancelRejectedWithOrderInfoDecoder; 
	private final OrderAmendedDecoder orderAmendedDecoder;
	private final OrderAmendRejectedDecoder orderAmendRejectedDecoder;
	private final OrderDecoder orderDecoder;
	private final TradeDecoder tradeDecoder;

	public static OrderAndTradeDecoderSupplier of(){
		return new OrderAndTradeDecoderSupplier();
	}
	
	OrderAndTradeDecoderSupplier(){
		this.orderAcceptedDecoder = OrderAcceptedDecoder.of();
		this.orderAcceptedWithOrderInfoDecoder = OrderAcceptedWithOrderInfoDecoder.of();
		this.orderCancelledDecoder = OrderCancelledDecoder.of();
		this.orderCancelledWithOrderInfoDecoder = OrderCancelledWithOrderInfoDecoder.of();
		this.orderExpiredDecoder = OrderExpiredDecoder.of();
		this.orderExpiredWithOrderInfoDecoder = OrderExpiredWithOrderInfoDecoder.of();
		this.orderRejectedDecoder = OrderRejectedDecoder.of();
		this.orderRejectedWithOrderInfoDecoder = OrderRejectedWithOrderInfoDecoder.of();
		this.tradeCreatedDecoder = TradeCreatedDecoder.of();
		this.tradeCreatedWithOrderInfoDecoder = TradeCreatedWithOrderInfoDecoder.of(); 
		this.tradeCancelledDecoder = TradeCancelledDecoder.of();
		this.orderDecoder = OrderDecoder.of();
		this.tradeDecoder = TradeDecoder.of();
		this.orderCancelRejectedDecoder  = OrderCancelRejectedDecoder.of();
		this.orderCancelRejectedWithOrderInfoDecoder  = OrderCancelRejectedWithOrderInfoDecoder.of();
		this.orderAmendedDecoder = OrderAmendedDecoder.of();
		this.orderAmendRejectedDecoder = OrderAmendRejectedDecoder.of();
	}

	public OrderAndTradeDecoderSupplier interceptOrderAccepted(Handler<OrderAcceptedSbeDecoder> interceptor){
		orderAcceptedDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderAcceptedWithOrderInfo(Handler<OrderAcceptedWithOrderInfoSbeDecoder> interceptor){
		orderAcceptedWithOrderInfoDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderCancelled(Handler<OrderCancelledSbeDecoder> interceptor){
		orderCancelledDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderCancelledWithOrderInfo(Handler<OrderCancelledWithOrderInfoSbeDecoder> interceptor){
		orderCancelledWithOrderInfoDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderExpired(Handler<OrderExpiredSbeDecoder> interceptor){
		orderExpiredDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderExpiredWithOrderInfo(Handler<OrderExpiredWithOrderInfoSbeDecoder> interceptor){
		orderExpiredWithOrderInfoDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderRejected(Handler<OrderRejectedSbeDecoder> interceptor){
		orderRejectedDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderRejectedWithOrderInfo(Handler<OrderRejectedWithOrderInfoSbeDecoder> interceptor){
		orderRejectedWithOrderInfoDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptTradeCreated(Handler<TradeCreatedSbeDecoder> interceptor){
		tradeCreatedDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptTradeCreatedWithOrderInfo(Handler<TradeCreatedWithOrderInfoSbeDecoder> interceptor){
		tradeCreatedWithOrderInfoDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptTradeCancelled(Handler<TradeCancelledSbeDecoder> interceptor){
		tradeCancelledDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderAmended(Handler<OrderAmendedSbeDecoder> interceptor){
		orderAmendedDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderAmendRejected(Handler<OrderAmendRejectedSbeDecoder> interceptor){
		orderAmendRejectedDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderCancelRejected(Handler<OrderCancelRejectedSbeDecoder> interceptor){
		orderCancelRejectedDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrderCancelRejectedWithOrderInfo(Handler<OrderCancelRejectedWithOrderInfoSbeDecoder> interceptor){
		orderCancelRejectedWithOrderInfoDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptTrade(Handler<TradeSbeDecoder> interceptor){
		tradeDecoder.interceptor(interceptor);
		return this;		
	}

	public OrderAndTradeDecoderSupplier interceptOrder(Handler<OrderSbeDecoder> interceptor){
		orderDecoder.interceptor(interceptor);
		return this;
	}

	public void clearAllInterceptors(){
		orderAcceptedDecoder.clearInterceptor();
		orderAcceptedWithOrderInfoDecoder.clearInterceptor();
		orderCancelledDecoder.clearInterceptor();
		orderCancelledWithOrderInfoDecoder.clearInterceptor();
		orderExpiredDecoder.clearInterceptor();
		orderExpiredWithOrderInfoDecoder.clearInterceptor();
		orderRejectedDecoder.clearInterceptor();
		orderRejectedWithOrderInfoDecoder.clearInterceptor();
		tradeCreatedDecoder.clearInterceptor();
		tradeCreatedWithOrderInfoDecoder.clearInterceptor();
		tradeCancelledDecoder.clearInterceptor();
		orderCancelRejectedDecoder.clearInterceptor();
		orderAmendedDecoder.clearInterceptor();
		orderAmendRejectedDecoder.clearInterceptor();
		orderDecoder.clearInterceptor();
		tradeDecoder.clearInterceptor();
	}
	
	public HandlerList<OrderSbeDecoder> orderHandlerList() {
		return orderDecoder.handlerList();
	}

	public HandlerList<OrderAcceptedSbeDecoder> orderAcceptedHandlerList() {
		return orderAcceptedDecoder.handlerList();
	}

	public HandlerList<OrderAcceptedWithOrderInfoSbeDecoder> orderAcceptedWithOrderInfoHandlerList() {
		return orderAcceptedWithOrderInfoDecoder.handlerList();
	}

	public HandlerList<OrderCancelledSbeDecoder> orderCancelledHandlerList() {
		return orderCancelledDecoder.handlerList();
	}

	public HandlerList<OrderCancelledWithOrderInfoSbeDecoder> orderCancelledWithOrderInfoHandlerList() {
		return orderCancelledWithOrderInfoDecoder.handlerList();
	}

	public HandlerList<OrderRejectedSbeDecoder> orderRejectedHandlerList() {
		return orderRejectedDecoder.handlerList();
	}

	public HandlerList<OrderRejectedWithOrderInfoSbeDecoder> orderRejectedWithOrderInfoHandlerList() {
		return orderRejectedWithOrderInfoDecoder.handlerList();
	}

	public HandlerList<OrderExpiredSbeDecoder> orderExpiredHandlerList() {
		return orderExpiredDecoder.handlerList();
	}

	public HandlerList<OrderExpiredWithOrderInfoSbeDecoder> orderExpiredWithOrderInfoHandlerList() {
		return orderExpiredWithOrderInfoDecoder.handlerList();
	}

	public HandlerList<TradeCreatedSbeDecoder> tradeCreatedHandlerList() {
		return tradeCreatedDecoder.handlerList();
	}

	public HandlerList<TradeCreatedWithOrderInfoSbeDecoder> tradeCreatedWithOrderInfoHandlerList() {
		return tradeCreatedWithOrderInfoDecoder.handlerList();
	}

	public HandlerList<TradeCancelledSbeDecoder> tradeCancelledHandlerList() {
		return tradeCancelledDecoder.handlerList();
	}

	public HandlerList<TradeSbeDecoder> tradeHandlerList() {
		return tradeDecoder.handlerList();
	}

	public HandlerList<OrderCancelRejectedSbeDecoder> orderCancelRejectedHandlerList() {
		return orderCancelRejectedDecoder.handlerList();
	}

	public HandlerList<OrderCancelRejectedWithOrderInfoSbeDecoder> orderCancelRejectedWithOrderInfoHandlerList() {
		return orderCancelRejectedWithOrderInfoDecoder.handlerList();
	}

	public HandlerList<OrderAmendedSbeDecoder> orderAmendedHandlerList() {
		return orderAmendedDecoder.handlerList();
	}

	public HandlerList<OrderAmendRejectedSbeDecoder> orderAmendRejectedHandlerList() {
		return orderAmendRejectedDecoder.handlerList();
	}

	public void register(MessageReceiver receiver) {
		receiver.registerDecoder(OrderSbeDecoder.TEMPLATE_ID, orderDecoder);
		receiver.registerDecoder(OrderAcceptedSbeDecoder.TEMPLATE_ID, orderAcceptedDecoder);
		receiver.registerDecoder(OrderAcceptedWithOrderInfoSbeDecoder.TEMPLATE_ID, orderAcceptedWithOrderInfoDecoder);
		receiver.registerDecoder(OrderCancelledSbeDecoder.TEMPLATE_ID, orderCancelledDecoder);
		receiver.registerDecoder(OrderCancelledWithOrderInfoSbeDecoder.TEMPLATE_ID, orderCancelledWithOrderInfoDecoder);
		receiver.registerDecoder(OrderExpiredSbeDecoder.TEMPLATE_ID, orderExpiredDecoder);
		receiver.registerDecoder(OrderExpiredWithOrderInfoSbeDecoder.TEMPLATE_ID, orderExpiredWithOrderInfoDecoder);
		receiver.registerDecoder(OrderCancelRejectedSbeDecoder.TEMPLATE_ID, orderCancelRejectedDecoder);
		receiver.registerDecoder(OrderCancelRejectedWithOrderInfoSbeDecoder.TEMPLATE_ID, orderCancelRejectedWithOrderInfoDecoder);
		receiver.registerDecoder(OrderRejectedSbeDecoder.TEMPLATE_ID, orderRejectedDecoder);
		receiver.registerDecoder(OrderRejectedWithOrderInfoSbeDecoder.TEMPLATE_ID, orderRejectedWithOrderInfoDecoder);
		receiver.registerDecoder(OrderAmendRejectedSbeDecoder.TEMPLATE_ID, orderAmendRejectedDecoder);
		receiver.registerDecoder(OrderAmendedSbeDecoder.TEMPLATE_ID, orderAmendedDecoder);
		receiver.registerDecoder(TradeSbeDecoder.TEMPLATE_ID, tradeDecoder);
		receiver.registerDecoder(TradeCreatedSbeDecoder.TEMPLATE_ID, tradeCreatedDecoder);
		receiver.registerDecoder(TradeCreatedWithOrderInfoSbeDecoder.TEMPLATE_ID, tradeCreatedWithOrderInfoDecoder);
		receiver.registerDecoder(TradeCancelledSbeDecoder.TEMPLATE_ID, tradeCancelledDecoder);
	}
}

