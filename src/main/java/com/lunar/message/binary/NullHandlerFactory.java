package com.lunar.message.binary;

import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.AmendOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandSbeDecoder;
import com.lunar.message.io.sbe.EchoSbeDecoder;
import com.lunar.message.io.sbe.ExchangeSbeDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.OrderCompletionSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.PerfDataSbeDecoder;
import com.lunar.message.io.sbe.PingSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.ServiceStatusSbeDecoder;
import com.lunar.message.io.sbe.TimerEventSbeDecoder;

public class NullHandlerFactory {
	public Handler<CommandSbeDecoder> commandHandler(){
		return CommandDecoder.NULL_HANDLER;
	}
	public Handler<CommandAckSbeDecoder> commandAckHandler(){
		return CommandAckDecoder.NULL_HANDLER;
	}
	public Handler<EchoSbeDecoder> echoHandler(){
		return EchoDecoder.NULL_HANDLER;
	}
	public Handler<ExchangeSbeDecoder> exchangeHandler(){
		return ExchangeDecoder.NULL_HANDLER;
	}
	public Handler<AggregateOrderBookUpdateSbeDecoder> marketDataOrderBookSnapshotHandler(){
		return AggregateOrderBookUpdateDecoder.NULL_HANDLER;
	}
	public Handler<OrderSbeDecoder> orderHandler(){
		return OrderDecoder.NULL_HANDLER;
	}
	public Handler<OrderCompletionSbeDecoder> orderCompletionHandler(){
		return OrderCompletionDecoder.NULL_HANDLER;
	}
	public Handler<NewOrderRequestSbeDecoder> newOrderRequestHandler(){
		return NewOrderRequestDecoder.NULL_HANDLER;
	}
	public Handler<CancelOrderRequestSbeDecoder> orderCancelHandler(){
		return CancelOrderRequestDecoder.NULL_HANDLER;
	}
	public Handler<AmendOrderRequestSbeDecoder> AmendOrderRequestHandler(){
		return AmendOrderRequestDecoder.NULL_HANDLER;
	}
	public Handler<PerfDataSbeDecoder> perfDataHandler(){
		return PerfDataDecoder.NULL_HANDLER;
	}
	public Handler<PingSbeDecoder> pingHandler(){
		return PingDecoder.NULL_HANDLER;
	}
	public Handler<RequestSbeDecoder> requestHandler(){
		return RequestDecoder.NULL_HANDLER;
	}
	public Handler<ResponseSbeDecoder> responseHandler(){
		return ResponseDecoder.NULL_HANDLER;
	}
	public Handler<SecuritySbeDecoder> securityHandler(){
		return SecurityDecoder.NULL_HANDLER;
	}
	public Handler<ServiceStatusSbeDecoder> serviceStatusHandler(){
		return ServiceStatusDecoder.NULL_HANDLER;
	}
	public Handler<TimerEventSbeDecoder> timerEventHandler(){
		return TimerEventDecoder.NULL_HANDLER;
	}	
	
}
