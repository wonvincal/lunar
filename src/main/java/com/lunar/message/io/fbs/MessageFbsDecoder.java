package com.lunar.message.io.fbs;

import java.nio.ByteBuffer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class MessageFbsDecoder {
	static final Logger LOG = LogManager.getLogger(MessageFbsDecoder.class);
	private final MessageFbs messageFbs = new MessageFbs();
	private final CommandFbs commandFbs = new CommandFbs();
	private final CommandAckFbs commandAckFbs = new CommandAckFbs();
	private final CancelOrderRequestFbs cancelOrderRequestFbs = new CancelOrderRequestFbs();
	private final SecurityFbs securityFbs = new SecurityFbs();
	private final ExchangeFbs exchangeFbs = new ExchangeFbs();
	private final RequestFbs requestFbs = new RequestFbs();
	private final ResponseFbs responseFbs = new ResponseFbs();
	private final AggregateOrderBookUpdateFbs aggregateOrderBookUpdateFbs = new AggregateOrderBookUpdateFbs();
	private final OrderBookSnapshotFbs orderBookSnapshotFbs = new OrderBookSnapshotFbs();
	private final NewOrderRequestFbs newOrderRequestFbs = new NewOrderRequestFbs();
    private final StrategyParametersFbs strategyParametersFbs = new StrategyParametersFbs();
    private final StrategyStatusFbs strategyStatusFbs = new StrategyStatusFbs();
    private final StrategySwitchFbs strategySwitchFbs = new StrategySwitchFbs();
    private final StrategyTypeFbs strategyTypeFbs = new StrategyTypeFbs();
    private final StrategyUndParametersFbs strategyUndParametersFbs = new StrategyUndParametersFbs();
    private final StrategyIssuerParametersFbs strategyIssuerParametersFbs = new StrategyIssuerParametersFbs();
    private final StrategyWrtParametersFbs strategyWrtParametersFbs = new StrategyWrtParametersFbs();
    private final IssuerFbs issuerFbs = new IssuerFbs();
    private final OrderFbs orderFbs = new OrderFbs();
    private final OrderRequestCompletionFbs orderRequestCompletionFbs = new OrderRequestCompletionFbs();
    private final TradeFbs tradeFbs = new TradeFbs();
    private final PositionFbs positionFbs = new PositionFbs();
    private final ScoreBoardSchemaFbs scoreBoardSchemaFbs = new ScoreBoardSchemaFbs();
    private final ScoreBoardFbs scoreBoardFbs = new ScoreBoardFbs();

	private byte payloadType;
	
	public static MessageFbsDecoder of(){
		return new MessageFbsDecoder();
	}
	
	MessageFbsDecoder(){
	}
	
	public byte init(ByteBuffer buffer){
		this.payloadType = MessageFbs.getRootAsMessageFbs(buffer, messageFbs).messagePayloadType();
		return this.payloadType;
	}
	
	public CommandFbs asCommandFbs(){
		if (this.payloadType == MessagePayloadFbs.CommandFbs){
			messageFbs.messagePayload(commandFbs);
			return commandFbs;
		}
		return null;
	}
	
	public CommandAckFbs asCommandAckFbs(){
		if (this.payloadType == MessagePayloadFbs.CommandAckFbs){
			messageFbs.messagePayload(commandAckFbs);
			return commandAckFbs;
		}
		return null;
	}
	
	public CancelOrderRequestFbs asCancelOrderRequestFbs(){
		if (this.payloadType == MessagePayloadFbs.CancelOrderRequestFbs){
			messageFbs.messagePayload(cancelOrderRequestFbs);
			return cancelOrderRequestFbs;
		}
		return null;
	}
	
	public NewOrderRequestFbs asNewOrderRequestFbs(){
		if (this.payloadType == MessagePayloadFbs.NewOrderRequestFbs){
			messageFbs.messagePayload(newOrderRequestFbs);
			return newOrderRequestFbs;
		}
		return null;
	}
	
	public RequestFbs asRequestFbs(){
		if (this.payloadType == MessagePayloadFbs.RequestFbs){
			messageFbs.messagePayload(requestFbs);
			return requestFbs;
		}
		return null;
	}
	
	public SecurityFbs asSecurityFbs(){
		if (this.payloadType == MessagePayloadFbs.SecurityFbs){
			messageFbs.messagePayload(securityFbs);
			return securityFbs;
		}
		return null;
	}
	
	public ExchangeFbs asExchangeFbs(){
		if (this.payloadType == MessagePayloadFbs.ExchangeFbs){
			messageFbs.messagePayload(exchangeFbs);
			return exchangeFbs;
		}
		return null;
	}
	
	public ResponseFbs asResponseFbs(){
		if (this.payloadType == MessagePayloadFbs.ResponseFbs){
			messageFbs.messagePayload(responseFbs);
			return responseFbs;
		}
		return null;
	}
	
	public PositionFbs asPositionFbs(){
		if (this.payloadType == MessagePayloadFbs.PositionFbs){
			messageFbs.messagePayload(positionFbs);
			return positionFbs;
		}
		return null;
	}

	public AggregateOrderBookUpdateFbs asAggregateOrderBookUpdateFbs(){
		if (this.payloadType == MessagePayloadFbs.AggregateOrderBookUpdateFbs){
			messageFbs.messagePayload(aggregateOrderBookUpdateFbs);
			return aggregateOrderBookUpdateFbs;
		}
		return null;
	}
	
	public OrderBookSnapshotFbs asOrderBookSnapshotFbs(){
		if (this.payloadType == MessagePayloadFbs.OrderBookSnapshotFbs){
			messageFbs.messagePayload(orderBookSnapshotFbs);
			return orderBookSnapshotFbs;
		}
		return null;
	}
	
	public StrategyParametersFbs asStrategyParametersFbs() {
	    if (this.payloadType == MessagePayloadFbs.StrategyParametersFbs) {
	        messageFbs.messagePayload(strategyParametersFbs);
	        return strategyParametersFbs;
	    }
	    return null;
	}
	
    public StrategyStatusFbs asStrategyStatusFbs() {
        if (this.payloadType == MessagePayloadFbs.StrategyStatusFbs) {
            messageFbs.messagePayload(strategyStatusFbs);
            return strategyStatusFbs;
        }
        return null;
    }

    public StrategySwitchFbs asStrategySwitchFbs() {
        if (this.payloadType == MessagePayloadFbs.StrategySwitchFbs) {
            messageFbs.messagePayload(strategySwitchFbs);
            return strategySwitchFbs;
        }
        return null;
    }
    
    public StrategyTypeFbs asStrategyTypeFbs() {
        if (this.payloadType == MessagePayloadFbs.StrategyTypeFbs) {
            messageFbs.messagePayload(strategyTypeFbs);
            return strategyTypeFbs;
        }
        return null;
    }
    
    public StrategyUndParametersFbs asStrategyUndParametersFbs() {
        if (this.payloadType == MessagePayloadFbs.StrategyUndParametersFbs) {
            messageFbs.messagePayload(strategyUndParametersFbs);
            return strategyUndParametersFbs;
        }
        return null;
    }

    public StrategyIssuerParametersFbs asStrategyIssuerParametersFbs() {
        if (this.payloadType == MessagePayloadFbs.StrategyIssuerParametersFbs) {
            messageFbs.messagePayload(strategyIssuerParametersFbs);
            return strategyIssuerParametersFbs;
        }
        return null;
    }
    
    public StrategyWrtParametersFbs asStrategyWrtParametersFbs() {
        if (this.payloadType == MessagePayloadFbs.StrategyWrtParametersFbs) {
            messageFbs.messagePayload(strategyWrtParametersFbs);
            return strategyWrtParametersFbs;
        }
        return null;
    }
    
    public IssuerFbs asIssuerFbs() {
        if (this.payloadType == MessagePayloadFbs.IssuerFbs) {
            messageFbs.messagePayload(issuerFbs);
            return issuerFbs;
        }
        return null;
    }
    
    public OrderFbs asOrderFbs() {
        if (this.payloadType == MessagePayloadFbs.OrderFbs) {
            messageFbs.messagePayload(orderFbs);
            return orderFbs;
        }
        return null;
    }    
    
    public OrderRequestCompletionFbs asOrderRequestCompletionFbs() {
        if (this.payloadType == MessagePayloadFbs.OrderRequestCompletionFbs) {
            messageFbs.messagePayload(orderRequestCompletionFbs);
            return orderRequestCompletionFbs;
        }
        return null;
    }
    
    public TradeFbs asTradeFbs() {
        if (this.payloadType == MessagePayloadFbs.TradeFbs) {
            messageFbs.messagePayload(tradeFbs);
            return tradeFbs;
        }
        return null;
    }    
    
    public ScoreBoardSchemaFbs asScoreBoardSchemaFbs(){
        if (this.payloadType == MessagePayloadFbs.ScoreBoardSchemaFbs){
            messageFbs.messagePayload(scoreBoardSchemaFbs);
            return scoreBoardSchemaFbs;
        }
        return null;
    }
    
    public ScoreBoardFbs asScoreBoardFbs(){
        if (this.payloadType == MessagePayloadFbs.ScoreBoardFbs){
            messageFbs.messagePayload(scoreBoardFbs);
            return scoreBoardFbs;
        }
        return null;
    }    
}
