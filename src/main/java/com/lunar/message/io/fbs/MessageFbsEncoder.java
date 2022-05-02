package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.Command;
import com.lunar.message.Request;
import com.lunar.message.ServiceStatus;
import com.lunar.message.io.sbe.AggregateOrderBookUpdateSbeDecoder;
import com.lunar.message.io.sbe.CancelOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.CommandAckSbeDecoder;
import com.lunar.message.io.sbe.CommandAckType;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NewOrderRequestSbeDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.RequestSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
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
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.order.CancelOrderRequest;
import com.lunar.order.NewOrderRequest;
import com.lunar.order.OrderRequest;
import com.lunar.service.ServiceConstant;

public class MessageFbsEncoder {
	private static int DEFAULT_INT_BUFFER_SIZE = 128;
	static final Logger LOG = LogManager.getLogger(MessageFbsEncoder.class);
	private ByteBuffer buffer;
	private ByteBuffer stringBuffer;
	private int[] intBuffer;
	private FlatBufferBuilder builder;
	private final SbeDecoderManager sbeDecoderMgr;
	
	public static MessageFbsEncoder of(){
		return new MessageFbsEncoder(ServiceConstant.MAX_MESSAGE_SIZE);
	}
	
	MessageFbsEncoder(int initialCapacity){
		buffer = ByteBuffer.allocateDirect(initialCapacity);
		byte[] array = new byte[initialCapacity];
		stringBuffer = ByteBuffer.wrap(array);
		builder = new FlatBufferBuilder(buffer);
		sbeDecoderMgr = SbeDecoderManager.of();
		intBuffer = new int[DEFAULT_INT_BUFFER_SIZE];
	}

	public ByteBuffer encodeCommand(ByteBuffer suppliedBuffer, Command command){
		builder.init(suppliedBuffer);
		int commandOffset = CommandFbsEncoder.toByteBufferInFbs(builder, stringBuffer, command);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.CommandFbs, commandOffset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}

	public ByteBuffer encodeCommandAck(ByteBuffer suppliedBuffer, int clientKey, CommandAckType ackType, CommandType commandType){
		builder.init(suppliedBuffer);
		int commandAckOffset = CommandAckFbsEncoder.toByteBufferInFbs(builder, stringBuffer, clientKey, ackType, commandType);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.CommandAckFbs, commandAckOffset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}
	
	public ByteBuffer encodeResponse(ByteBuffer suppliedBuffer, DirectBuffer buffer, int offset, MessageHeaderDecoder header, ResponseSbeDecoder response){
		builder.init(suppliedBuffer);
		int responseOffset = ResponseFbsEncoder.toByteBufferInFbs(builder, stringBuffer, buffer, offset, header, response, sbeDecoderMgr, intBuffer);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ResponseFbs, responseOffset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}

	ByteBuffer encodeCommandAckWithClientKey(ByteBuffer suppliedBuffer,
			CommandAckSbeDecoder commandAck,
			int clientKey){
		builder.init(suppliedBuffer);
		int offset = CommandAckFbsEncoder.toByteBufferInFbs(builder, stringBuffer, clientKey, commandAck.ackType(), commandAck.commandType());
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.CommandAckFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}
	
	public ByteBuffer encodeResponseWithClientKey(ByteBuffer suppliedBuffer, 
			DirectBuffer buffer, 
			int offset, 
			MessageHeaderDecoder header,
			ResponseSbeDecoder response,
			int clientKey){
		builder.init(suppliedBuffer);
		int responseOffset = ResponseFbsEncoder.toByteBufferInFbs(builder, stringBuffer, buffer, offset, header, response, clientKey, sbeDecoderMgr, intBuffer);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ResponseFbs, responseOffset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}

	public ByteBuffer encodeResponse(ByteBuffer suppliedBuffer, int clientKey, boolean isLast, byte resultType, ServiceStatus status){
		builder.init(suppliedBuffer);
		int offset = ResponseFbsEncoder.toByteBufferInFbs(builder, stringBuffer, clientKey, isLast, resultType, status);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ResponseFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}

	public ByteBuffer encodeResponse(ByteBuffer suppliedBuffer, int clientKey, boolean isLast, byte resultType, SecuritySbeDecoder security){
		builder.init(suppliedBuffer);
		int offset = ResponseFbsEncoder.toByteBufferInFbs(builder, stringBuffer, clientKey, isLast, resultType, security);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ResponseFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}

	public ByteBuffer encodeResponse(ByteBuffer suppliedBuffer, int clientKey, boolean isLast, byte resultType){
		builder.init(suppliedBuffer);
		int offset = ResponseFbsEncoder.toByteBufferInFbs(builder, stringBuffer, clientKey, isLast, resultType);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ResponseFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}

	public ByteBuffer encodeResponse(int clientKey, boolean isLast, byte resultType){
		builder.init(buffer);
		int offset = ResponseFbsEncoder.toByteBufferInFbs(builder, stringBuffer, clientKey, isLast, resultType);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ResponseFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}
	
	public ByteBuffer encodeRequest(RequestSbeDecoder request){
		builder.init(buffer);
		int offset = RequestFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.RequestFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}
	
	public ByteBuffer encodeSecurity(SecuritySbeDecoder security){
		builder.init(buffer);
		int securityEndOffset = SecurityFbsEncoder.toByteBufferInFbs(builder, stringBuffer, security);
		int messageEndOffset = MessageFbs.createMessageFbs(builder,MessagePayloadFbs.SecurityFbs, securityEndOffset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}
	
	public ByteBuffer encodeSecurity(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, SecuritySbeDecoder security){
		builder.init(suppliedBuffer);
		int securityEndOffset = SecurityFbsEncoder.toByteBufferInFbs(builder, stringBuffer, security);
		int messageEndOffset = MessageFbs.createMessageFbs(builder,MessagePayloadFbs.SecurityFbs, securityEndOffset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}

	public ByteBuffer encodeRequest(Request request){
		builder.init(buffer);
		int offset = RequestFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.RequestFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}
	
	public ByteBuffer encodeRequest(ByteBuffer suppliedBuffer, Request request){
		builder.init(suppliedBuffer);
		int offset = RequestFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.RequestFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}

	public ByteBuffer encodeOrder(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, OrderSbeDecoder order){
        builder.init(suppliedBuffer);
        int offset = OrderFbsEncoder.toByteBufferInFbs(builder, stringBuffer, order);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.OrderFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
    public ByteBuffer encodeNewOrderRequest(ByteBuffer suppliedBuffer, NewOrderRequest request){
        builder.init(suppliedBuffer);
        int offset = OrderFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.NewOrderRequestFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }

    public ByteBuffer encodeNewOrderRequest(ByteBuffer suppliedBuffer, NewOrderRequestSbeDecoder request){
        builder.init(suppliedBuffer);
        int offset = OrderFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.NewOrderRequestFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }

    public ByteBuffer encodeCancelOrderRequest(ByteBuffer suppliedBuffer, CancelOrderRequest request){
        builder.init(suppliedBuffer);
        int offset = OrderFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.CancelOrderRequestFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }

    public ByteBuffer encodeCancelOrderRequest(ByteBuffer suppliedBuffer, CancelOrderRequestSbeDecoder request){
        builder.init(suppliedBuffer);
        int offset = OrderFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.CancelOrderRequestFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }

    public ByteBuffer encodeEvent(ByteBuffer suppliedBuffer, 
			int sinkId,
    		long time,
			byte category,
			byte level,
			byte eventType,
			String description){		
        builder.init(suppliedBuffer);
        int offset = EventFbsEncoder.toByteBufferInFbs(builder, 
    			sinkId,
    			time,
    			category,
    			level,
    			eventType,
    			description);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.EventFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }    
    
    public ByteBuffer encodeEvent(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, EventSbeDecoder event){
        builder.init(suppliedBuffer);
        int offset = EventFbsEncoder.toByteBufferInFbs(builder, stringBuffer, event);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.EventFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }

    public ByteBuffer encodeTrade(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, TradeSbeDecoder trade){
        builder.init(suppliedBuffer);
        int offset = TradeFbsEncoder.toByteBufferInFbs(builder, stringBuffer, trade);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.TradeFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
    public ByteBuffer encodePosition(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, PositionSbeDecoder position){
        builder.init(suppliedBuffer);
        int offset = PositionFbsEncoder.toByteBufferInFbs(builder, stringBuffer, position);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.PositionFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }

    public ByteBuffer encodeRiskState(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, RiskStateSbeDecoder riskState){
        builder.init(suppliedBuffer);
        int offset = RiskStateFbsEncoder.toByteBufferInFbs(builder, stringBuffer, riskState);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.RiskStateFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }

    public ByteBuffer encodeServiceStatus(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, ServiceStatusSbeDecoder status){
        builder.init(suppliedBuffer);
        int offset = ServiceStatusFbsEncoder.toByteBufferInFbs(builder, stringBuffer, status);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ServiceStatusFbs, offset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
	public ByteBuffer encodeAggregateOrderBookUpdate(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, AggregateOrderBookUpdateSbeDecoder md){
		builder.init(suppliedBuffer);
		int offset = AggregateOrderBookUpdateFbsEncoder.toByteBufferInFbs(builder, stringBuffer, md);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.AggregateOrderBookUpdateFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}
	
	public ByteBuffer encodeOrderBookSnapshot(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, OrderBookSnapshotSbeDecoder md){
		builder.init(suppliedBuffer);
		int offset = OrderBookSnapshotFbsEncoder.toByteBufferInFbs(builder, stringBuffer, md);
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.OrderBookSnapshotFbs, offset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();
	}

//    public ByteBuffer encodeIssuer(IssuerSbeDecoder issuer) {
//        return encodeIssuer(buffer, issuer);
//    }
//	
	public ByteBuffer encodeIssuer(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, IssuerSbeDecoder issuer) {
	    builder.init(suppliedBuffer);
	    int issuerEndOffset = IssuerFbsEncoder.toByteBufferInFbs(builder, stringBuffer, issuer);
	    int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.IssuerFbs, issuerEndOffset);
	    builder.finish(messageEndOffset);
	    return builder.dataBuffer();
	}

//    public ByteBuffer encodeStrategyType(StrategyTypeSbeDecoder strategyType) {
//        return encodeStrategyType(buffer, strategyType);
//    }
//	
	public ByteBuffer encodeStrategyType(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, StrategyTypeSbeDecoder strategyType) {
	    builder.init(suppliedBuffer);
	    int strategyTypeEndOffset = StrategyTypeFbsEncoder.toByteBufferInFbs(builder, stringBuffer, strategyType);
	    int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.StrategyTypeFbs, strategyTypeEndOffset);
	    builder.finish(messageEndOffset);
	    return builder.dataBuffer();
	}

//    public ByteBuffer encodeStrategyParams(StrategyParamsSbeDecoder strategyParams) {
//        return encodeStrategyParams(buffer, strategyParams);
//    }
//    
    public ByteBuffer encodeStrategyParams(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, StrategyParamsSbeDecoder strategyParams) {
        builder.init(suppliedBuffer);
        int strategyParamsEndOffset = StrategyParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, strategyParams);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.StrategyParametersFbs, strategyParamsEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }    

//    public ByteBuffer encodeStrategyUndParams(StrategyUndParamsSbeDecoder strategyUndParams) {
//        return encodeStrategyUndParams(buffer, strategyUndParams);
//    }   
//
    public ByteBuffer encodeStrategyUndParams(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, StrategyUndParamsSbeDecoder strategyUndParams) {
        builder.init(suppliedBuffer);
        int strategyParamsEndOffset = StrategyUndParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, strategyUndParams);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.StrategyUndParametersFbs, strategyParamsEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }   

//    public ByteBuffer encodeStrategyIssuerParams(StrategyIssuerParamsSbeDecoder strategyIssuerParams) {
//        return encodeStrategyIssuerParams(buffer, strategyIssuerParams);
//    }   
//
    public ByteBuffer encodeStrategyIssuerParams(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, StrategyIssuerParamsSbeDecoder strategyIssuerParams) {
        builder.init(suppliedBuffer);
        int strategyParamsEndOffset = StrategyIssuerParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, strategyIssuerParams);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.StrategyIssuerParametersFbs, strategyParamsEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }   
    
    public ByteBuffer encodeStrategyIssuerUndParams(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, StrategyIssuerUndParamsSbeDecoder strategyIssuerUndParams) {
        builder.init(suppliedBuffer);
        int strategyParamsEndOffset = StrategyIssuerUndParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, strategyIssuerUndParams);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.StrategyIssuerUndParametersFbs, strategyParamsEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
//    public ByteBuffer encodeStrategyWrtParams(StrategyWrtParamsSbeDecoder strategyWrtParams) {
//        return encodeStrategyWrtParams(buffer, strategyWrtParams);
//    }   
//
    public ByteBuffer encodeStrategyWrtParams(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, StrategyWrtParamsSbeDecoder strategyWrtParams) {
        builder.init(suppliedBuffer);
        int strategyParamsEndOffset = StrategyWrtParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, strategyWrtParams);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.StrategyWrtParametersFbs, strategyParamsEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }   

    public ByteBuffer encodeStrategySwitch(MessageHeaderDecoder header, StrategySwitchSbeDecoder strategySwitch) {
        return encodeStrategySwitch(buffer, header, strategySwitch);
    }
    
    public ByteBuffer encodeStrategySwitch(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, StrategySwitchSbeDecoder strategySwitch) {
        builder.init(suppliedBuffer);
        int strategySwitchEndOffset = StrategySwitchFbsEncoder.toByteBufferInFbs(builder, stringBuffer, header.senderSinkId(), strategySwitch);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.StrategySwitchFbs, strategySwitchEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
    public ByteBuffer encodeOrderRequestCompletionWithClientKey(ByteBuffer suppliedBuffer, OrderRequest request, int clientKey){
    	builder.init(suppliedBuffer);
    	int payloadEndOffset = OrderRequestCompletionFbsEncoder.toByteBufferInFbs(builder, stringBuffer, request, clientKey);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.OrderRequestCompletionFbs, payloadEndOffset);
        builder.finish(messageEndOffset);
    	return builder.dataBuffer(); 
    }

    public ByteBuffer encodeMarketStats(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, MarketStatsSbeDecoder marketStats) {
        builder.init(suppliedBuffer);
        int marketStatsEndOffset = MarketStatsFbsEncoder.toByteBufferInFbs(builder, stringBuffer, marketStats);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.MarketStatsFbs, marketStatsEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
//    public ByteBuffer encodeScoreBoardSchema(ScoreBoardSchemaSbeDecoder schema) {
//        return encodeScoreBoardSchema(buffer, schema);
//    }
//
    public ByteBuffer encodeScoreBoardSchema(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, ScoreBoardSchemaSbeDecoder schema) {
        builder.init(suppliedBuffer);
        int schemaEndOffset = ScoreBoardSchemaFbsEncoder.toByteBufferInFbs(builder, stringBuffer, schema);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ScoreBoardSchemaFbs, schemaEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
//    public ByteBuffer encodeScoreBoard(ScoreBoardSbeDecoder scoreBoard) {
//        return encodeScoreBoard(buffer, scoreBoard);
//    }
//    
    public ByteBuffer encodeScoreBoard(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, ScoreBoardSbeDecoder scoreBoard) {
        builder.init(suppliedBuffer);
        int scoreBoardEndOffset = ScoreBoardFbsEncoder.toByteBufferInFbs(builder, stringBuffer, scoreBoard);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ScoreBoardFbs, scoreBoardEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();
    }
    
//    public ByteBuffer encodeNote(NoteSbeDecoder note) {
//        return encodeNote(buffer, note);
//    }
    
    public ByteBuffer encodeNote(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, NoteSbeDecoder note) {
        builder.init(suppliedBuffer);
        int noteEndOffset = NoteFbsEncoder.toByteBufferInFbs(builder, stringBuffer, note);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.NoteFbs, noteEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();    	
    }
    
    public ByteBuffer encodeChartData(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, ChartDataSbeDecoder chartData) {
        builder.init(suppliedBuffer);
        int chartDataEndOffset = ChartDataFbsEncoder.toByteBufferInFbs(builder, stringBuffer, chartData, intBuffer);
        int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.ChartDataFbs, chartDataEndOffset);
        builder.finish(messageEndOffset);
        return builder.dataBuffer();    	
    }

	public ByteBuffer encodeCommandAck(ByteBuffer suppliedBuffer, MessageHeaderDecoder header, CommandAckSbeDecoder commandAck){
		builder.init(suppliedBuffer);
		int commandAckOffset = CommandAckFbsEncoder.toByteBufferInFbs(builder, stringBuffer, commandAck.clientKey(), commandAck.ackType(), commandAck.commandType());
		int messageEndOffset = MessageFbs.createMessageFbs(builder, MessagePayloadFbs.CommandAckFbs, commandAckOffset);
		builder.finish(messageEndOffset);
		return builder.dataBuffer();		
	}

}
