package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.flatbuffers.FlatBufferBuilder;
import com.lunar.message.ServiceStatus;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ChartDataSbeDecoder;
import com.lunar.message.io.sbe.EventSbeDecoder;
import com.lunar.message.io.sbe.IssuerSbeDecoder;
import com.lunar.message.io.sbe.MarketStatsSbeDecoder;
import com.lunar.message.io.sbe.MessageHeaderDecoder;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.OrderBookSnapshotSbeDecoder;
import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.PositionSbeDecoder;
import com.lunar.message.io.sbe.ResponseSbeDecoder;
import com.lunar.message.io.sbe.RiskControlSbeDecoder;
import com.lunar.message.io.sbe.RiskStateSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSbeDecoder;
import com.lunar.message.io.sbe.ScoreBoardSchemaSbeDecoder;
import com.lunar.message.io.sbe.SecuritySbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategySwitchSbeDecoder;
import com.lunar.message.io.sbe.StrategyTypeSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.TradeSbeDecoder;

public class ResponseFbsEncoder {
	static final Logger LOG = LogManager.getLogger(ResponseFbsEncoder.class);

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, int clientKey, boolean isLast, byte resultType){
		ResponseFbs.startResponseFbs(builder);
		ResponseFbs.addClientKey(builder, clientKey);
		ResponseFbs.addIsLast(builder, isLast);
		ResponseFbs.addResultType(builder, resultType);
		ResponseFbs.addMessagePayloadType(builder, MessagePayloadFbs.NONE);
		return ResponseFbs.endResponseFbs(builder);
	}
	
	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, int clientKey, boolean isLast, byte resultType, SecuritySbeDecoder security){
		return ResponseFbs.createResponseFbs(builder, 
				clientKey, 
				isLast, 
				resultType, 
				MessagePayloadFbs.SecurityFbs, 
				SecurityFbsEncoder.toByteBufferInFbs(builder, stringBuffer, security));
	}

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, int clientKey, boolean isLast, byte resultType, ServiceStatus status){
		return ResponseFbs.createResponseFbs(builder, 
				clientKey,
				isLast,
				resultType, 
				MessagePayloadFbs.ServiceStatusFbs, 
				ServiceStatusFbsEncoder.toByteBufferInFbs(builder, stringBuffer, status));
	}

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, DirectBuffer buffer, int offset, MessageHeaderDecoder header,
			ResponseSbeDecoder response, SbeDecoderManager sbeDecoderMgr, int[] intBuffer){
		return toByteBufferInFbs(builder, stringBuffer, buffer, offset, header, response, response.clientKey(), sbeDecoderMgr, intBuffer);
	}

	public static int toByteBufferInFbs(FlatBufferBuilder builder, ByteBuffer stringBuffer, DirectBuffer buffer, int offset,
			MessageHeaderDecoder header,
			ResponseSbeDecoder response,
			int clientKey, 
			SbeDecoderManager sbeDecoderMgr,
			int[] intBuffer){
		if (response.embeddedBlockLength() == 0 || response.embeddedBlockLength() == ResponseSbeDecoder.embeddedBlockLengthNullValue()){
			ResponseFbs.startResponseFbs(builder);
			ResponseFbs.addClientKey(builder, clientKey);
			ResponseFbs.addIsLast(builder, (response.isLast() == BooleanType.TRUE) ? true : false);
			ResponseFbs.addResultType(builder, response.resultType().value());
			return ResponseFbs.endResponseFbs(builder);
		}

		int result = 0;
		int limit = response.limit();
		final int sizeOfLengthField = 1;
		TemplateType templateType = TemplateType.get((byte)response.embeddedTemplateId());
		switch (templateType){
		case EVENT:
			EventSbeDecoder eventDecoder = sbeDecoderMgr.eventDecoder();
			eventDecoder.wrap(buffer, 
					offset + response.encodedLength() + sizeOfLengthField,
					EventSbeDecoder.BLOCK_LENGTH, 
					EventSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.EventFbs, 
					EventFbsEncoder.toByteBufferInFbs(builder, stringBuffer, eventDecoder));
			break;
		case ORDER:
			OrderSbeDecoder orderDecoder = sbeDecoderMgr.orderDecoder();
			orderDecoder.wrap(buffer, 
					offset + response.encodedLength() + sizeOfLengthField,
					OrderSbeDecoder.BLOCK_LENGTH, 
					OrderSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.OrderFbs, 
					OrderFbsEncoder.toByteBufferInFbs(builder, stringBuffer, orderDecoder));
			break;
		case POSITION:
			PositionSbeDecoder positionDecoder = sbeDecoderMgr.positionDecoder();
			positionDecoder.wrap(buffer, 
					offset + response.encodedLength() + sizeOfLengthField,
					PositionSbeDecoder.BLOCK_LENGTH, 
					PositionSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.PositionFbs, 
					PositionFbsEncoder.toByteBufferInFbs(builder, stringBuffer, positionDecoder));
			break;
		case RISK_CONTROL:
			RiskControlSbeDecoder riskControlDecoder = sbeDecoderMgr.riskControlDecoder();
			riskControlDecoder.wrap(buffer, 
					offset + response.encodedLength() + sizeOfLengthField,
					RiskControlSbeDecoder.BLOCK_LENGTH, 
					RiskControlSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.RiskControlFbs, 
					RiskControlFbsEncoder.toByteBufferInFbs(builder, stringBuffer, riskControlDecoder));
			break;
		case RISK_STATE:
			RiskStateSbeDecoder riskStateDecoder = sbeDecoderMgr.riskStateDecoder();
			riskStateDecoder.wrap(buffer, 
					offset + response.encodedLength() + sizeOfLengthField,
					RiskStateSbeDecoder.BLOCK_LENGTH, 
					RiskStateSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.RiskStateFbs, 
					RiskStateFbsEncoder.toByteBufferInFbs(builder, stringBuffer, riskStateDecoder));
			break;
		case SECURITY:
			SecuritySbeDecoder securityDecoder = sbeDecoderMgr.securityDecoder();
			securityDecoder.wrap(buffer, 
					offset + response.encodedLength() + sizeOfLengthField,
					SecuritySbeDecoder.BLOCK_LENGTH, 
					SecuritySbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.SecurityFbs, 
					SecurityFbsEncoder.toByteBufferInFbs(builder, stringBuffer, securityDecoder));
			break;
		case ISSUER:
			IssuerSbeDecoder issuerDecoder = sbeDecoderMgr.issuerDecoder();
			issuerDecoder.wrap(buffer,
					offset + response.encodedLength() + sizeOfLengthField,
					IssuerSbeDecoder.BLOCK_LENGTH, 
					IssuerSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.IssuerFbs, 
					IssuerFbsEncoder.toByteBufferInFbs(builder, stringBuffer, issuerDecoder));
			break;
		case STRATEGYTYPE:
			StrategyTypeSbeDecoder strategyTypeDecoder = new StrategyTypeSbeDecoder();
			strategyTypeDecoder.wrap(buffer,
					offset + response.encodedLength() + sizeOfLengthField,
					StrategyTypeSbeDecoder.BLOCK_LENGTH, 
					StrategyTypeSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.StrategyTypeFbs, 
					StrategyTypeFbsEncoder.toByteBufferInFbs(builder, stringBuffer, strategyTypeDecoder));
			break;
		case TRADE:
			TradeSbeDecoder tradeDecoder = sbeDecoderMgr.tradeDecoder();
			tradeDecoder.wrap(buffer, 
					offset + response.encodedLength() + sizeOfLengthField,
					TradeSbeDecoder.BLOCK_LENGTH, 
					TradeSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.TradeFbs, 
					TradeFbsEncoder.toByteBufferInFbs(builder, stringBuffer, tradeDecoder));
			if (LOG.isTraceEnabled()){
				LOG.trace("Encode embedded trade into fbs [channelId:{}, tradeSid:{}, orderSid:{}, secSid:{}, status:{}, price:{}, qty:{}]",
        			tradeDecoder.channelId(), tradeDecoder.tradeSid(), tradeDecoder.orderSid(), tradeDecoder.secSid(), tradeDecoder.tradeStatus().name(), tradeDecoder.executionPrice(), tradeDecoder.executionQty());
			}
			break;
		case STRATPARAMUPDATE:
            StrategyParamsSbeDecoder stratParamsSbeDecoder = sbeDecoderMgr.stratParamsSbeDecoder();
            stratParamsSbeDecoder.wrap(buffer,
                    offset + response.encodedLength() + sizeOfLengthField,
                    StrategyParamsSbeDecoder.BLOCK_LENGTH,
                    StrategyParamsSbeDecoder.SCHEMA_VERSION);
            result = ResponseFbs.createResponseFbs(builder, 
                    clientKey, 
                    ((response.isLast() == BooleanType.TRUE) ? true: false), 
                    response.resultType().value(), 
                    MessagePayloadFbs.StrategyParametersFbs, 
                    StrategyParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, stratParamsSbeDecoder));
	            break;	    
		case STRATWRTPARAMUPDATE:
			StrategyWrtParamsSbeDecoder stratWrtParamsSbeDecoder = sbeDecoderMgr.stratWrtParamsSbeDecoder();
			stratWrtParamsSbeDecoder.wrap(buffer,
					offset + response.encodedLength() + sizeOfLengthField,
					StrategyWrtParamsSbeDecoder.BLOCK_LENGTH,
					StrategyWrtParamsSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.StrategyWrtParametersFbs, 
					StrategyWrtParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, stratWrtParamsSbeDecoder));
			break;
        case STRATUNDPARAMUPDATE:
            StrategyUndParamsSbeDecoder stratUndParamsSbeDecoder = sbeDecoderMgr.stratUndParamsSbeDecoder();
            stratUndParamsSbeDecoder.wrap(buffer,
                    offset + response.encodedLength() + sizeOfLengthField,
                    StrategyUndParamsSbeDecoder.BLOCK_LENGTH,
                    StrategyUndParamsSbeDecoder.SCHEMA_VERSION);
            result = ResponseFbs.createResponseFbs(builder, 
                    clientKey, 
                    ((response.isLast() == BooleanType.TRUE) ? true: false), 
                    response.resultType().value(), 
                    MessagePayloadFbs.StrategyUndParametersFbs, 
                    StrategyUndParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, stratUndParamsSbeDecoder));
            break;
        case STRATISSUERPARAMUPDATE:
            StrategyIssuerParamsSbeDecoder stratIssuerParamsSbeDecoder = sbeDecoderMgr.stratIssuerParamsSbeDecoder();
            stratIssuerParamsSbeDecoder.wrap(buffer,
                    offset + response.encodedLength() + sizeOfLengthField,
                    StrategyIssuerParamsSbeDecoder.BLOCK_LENGTH,
                    StrategyIssuerParamsSbeDecoder.SCHEMA_VERSION);
            result = ResponseFbs.createResponseFbs(builder, 
                    clientKey, 
                    ((response.isLast() == BooleanType.TRUE) ? true: false), 
                    response.resultType().value(), 
                    MessagePayloadFbs.StrategyIssuerParametersFbs, 
                    StrategyIssuerParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, stratIssuerParamsSbeDecoder));
            break;
        case STRAT_ISSUER_UND_PARAM_UPDATE:
            StrategyIssuerUndParamsSbeDecoder stratIssuerUndParamsSbeDecoder = sbeDecoderMgr.stratIssuerUndParamsSbeDecoder();
            stratIssuerUndParamsSbeDecoder.wrap(buffer,
                    offset + response.encodedLength() + sizeOfLengthField,
                    StrategyIssuerUndParamsSbeDecoder.BLOCK_LENGTH,
                    StrategyIssuerUndParamsSbeDecoder.SCHEMA_VERSION);
            result = ResponseFbs.createResponseFbs(builder, 
                    clientKey, 
                    ((response.isLast() == BooleanType.TRUE) ? true: false), 
                    response.resultType().value(), 
                    MessagePayloadFbs.StrategyIssuerUndParametersFbs, 
                    StrategyIssuerUndParametersFbsEncoder.toByteBufferInFbs(builder, stringBuffer, stratIssuerUndParamsSbeDecoder));
            break;        
		case StrategySwitch:
			StrategySwitchSbeDecoder stratSwitchSbeDecoder = sbeDecoderMgr.stratSwitchSbeDecoder();
			stratSwitchSbeDecoder.wrap(buffer,
					offset + response.encodedLength() + sizeOfLengthField,
					StrategySwitchSbeDecoder.BLOCK_LENGTH,
					StrategySwitchSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.StrategySwitchFbs, 
					StrategySwitchFbsEncoder.toByteBufferInFbs(builder, stringBuffer, header.senderSinkId(), stratSwitchSbeDecoder));
			break;
		case ORDERBOOK_SNAPSHOT:
			OrderBookSnapshotSbeDecoder orderBookSbeDecoder = sbeDecoderMgr.orderBookSnapshotSbeDecoder();
			orderBookSbeDecoder.wrap(buffer,
					offset + response.encodedLength() + sizeOfLengthField,
					OrderBookSnapshotSbeDecoder.BLOCK_LENGTH,
					OrderBookSnapshotSbeDecoder.SCHEMA_VERSION);
			result = ResponseFbs.createResponseFbs(builder, 
					clientKey, 
					((response.isLast() == BooleanType.TRUE) ? true: false), 
					response.resultType().value(), 
					MessagePayloadFbs.OrderBookSnapshotFbs, 
					OrderBookSnapshotFbsEncoder.toByteBufferInFbs(builder, stringBuffer, orderBookSbeDecoder));
            break;
        case MARKET_STATS:
            MarketStatsSbeDecoder marketStatsSbeDecoder = sbeDecoderMgr.marketStatsSbeDecoder();
            marketStatsSbeDecoder.wrap(buffer,
                    offset + response.encodedLength() + sizeOfLengthField,
                    MarketStatsSbeDecoder.BLOCK_LENGTH,
                    MarketStatsSbeDecoder.SCHEMA_VERSION);
            result = ResponseFbs.createResponseFbs(builder, 
                    clientKey, 
                    ((response.isLast() == BooleanType.TRUE) ? true: false), 
                    response.resultType().value(), 
                    MessagePayloadFbs.MarketStatsFbs, 
                    MarketStatsFbsEncoder.toByteBufferInFbs(builder, stringBuffer, marketStatsSbeDecoder));
            break;
        case SCOREBOARD_SCHEMA:
            ScoreBoardSchemaSbeDecoder scoreBoardSchemaSbeDecoder = sbeDecoderMgr.scoreBoardSchemaSbeDecoder();
            scoreBoardSchemaSbeDecoder.wrap(buffer,
                    offset + response.encodedLength() + sizeOfLengthField,
                    ScoreBoardSchemaSbeDecoder.BLOCK_LENGTH,
                    ScoreBoardSchemaSbeDecoder.SCHEMA_VERSION);
            result = ResponseFbs.createResponseFbs(builder, 
                    clientKey, 
                    ((response.isLast() == BooleanType.TRUE) ? true: false), 
                    response.resultType().value(), 
                    MessagePayloadFbs.ScoreBoardSchemaFbs, 
                    ScoreBoardSchemaFbsEncoder.toByteBufferInFbs(builder, stringBuffer, scoreBoardSchemaSbeDecoder));
            break;   
        case SCOREBOARD:
            ScoreBoardSbeDecoder scoreBoardSbeDecoder = sbeDecoderMgr.scoreBoardSbeDecoder();
            scoreBoardSbeDecoder.wrap(buffer,
                    offset + response.encodedLength() + sizeOfLengthField,
                    ScoreBoardSbeDecoder.BLOCK_LENGTH,
                    ScoreBoardSbeDecoder.SCHEMA_VERSION);
            result = ResponseFbs.createResponseFbs(builder, 
                    clientKey, 
                    ((response.isLast() == BooleanType.TRUE) ? true: false), 
                    response.resultType().value(), 
                    MessagePayloadFbs.ScoreBoardFbs, 
                    ScoreBoardFbsEncoder.toByteBufferInFbs(builder, stringBuffer, scoreBoardSbeDecoder));
            break;
        case NOTE:
        	NoteSbeDecoder noteSbeDecoder = sbeDecoderMgr.noteSbeDecoder();
        	noteSbeDecoder.wrap(buffer, 
        			offset + response.encodedLength() + sizeOfLengthField,
        			NoteSbeDecoder.BLOCK_LENGTH,
        			NoteSbeDecoder.SCHEMA_VERSION);
        	result = ResponseFbs.createResponseFbs(builder, 
        			clientKey, 
        			((response.isLast() == BooleanType.TRUE) ? true: false), 
        			response.resultType().value(), 
        			MessagePayloadFbs.NoteFbs, 
        			NoteFbsEncoder.toByteBufferInFbs(builder, stringBuffer, noteSbeDecoder));
        	break;
        case CHART_DATA:
        	ChartDataSbeDecoder chartDataSbeDecoder = sbeDecoderMgr.chartDataSbeDecoder();
        	chartDataSbeDecoder.wrap(buffer, 
        			offset + response.encodedLength() + sizeOfLengthField, 
        			ChartDataSbeDecoder.BLOCK_LENGTH, 
        			ChartDataSbeDecoder.SCHEMA_VERSION);
        	result = ResponseFbs.createResponseFbs(builder, 
        			clientKey, 
        			((response.isLast() == BooleanType.TRUE) ? true: false), 
        			response.resultType().value(), 
        			MessagePayloadFbs.ChartDataFbs, 
        			ChartDataFbsEncoder.toByteBufferInFbs(builder, stringBuffer, chartDataSbeDecoder, intBuffer));
        	break;
        default:
                LOG.error("Encoding to fbs response not supported [templateType:" + templateType.name() + "]" );
		}
		response.limit(limit);
		return result;
	}
}
