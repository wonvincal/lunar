package com.lunar.message;

import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.TemplateType;

/**
 * 
 * @author Calvin
 *
 */
public class Response extends Message {
	private final int clientKey;
	private final int senderSinkId;
	private final BooleanType isLast;
	private final int responseMsgSeq;
	private final ResultType resultType;
	private final int embeddedBlockLength;
	private final int embeddedTemplateId;
	
	protected Response(int senderSinkId, int clientKey, BooleanType isLast, int responseMsgSeq, ResultType resultType, int embeddedBlockLength, int embeddedTemplateId){
		this.senderSinkId = senderSinkId;
		this.clientKey = clientKey;
		this.resultType = resultType;
		this.responseMsgSeq = responseMsgSeq;
		this.isLast = isLast;
		this.embeddedBlockLength = embeddedBlockLength;
		this.embeddedTemplateId = embeddedTemplateId;
	}
	
	public static Response of(int senderSinkId, int clientKey, BooleanType isLast, int responseMsgSeq, ResultType resultType, int embeddedBlockLength, int embeddedTemplateId){
		return new Response(senderSinkId, clientKey, isLast, responseMsgSeq, resultType, embeddedBlockLength, embeddedTemplateId);
	}
	
	public static Response of(int senderSinkId, int clientKey, BooleanType isLast, int responseMsgSeq, ResultType resultType){
		return new Response(senderSinkId, clientKey, isLast, responseMsgSeq, resultType, 0, TemplateType.NULL_VAL.value());
	}

	@Override
	public int senderSinkId() {
		return senderSinkId;
	}

	public int clientKey() {
		return clientKey;
	}

	public int responseMsgSeq() {
		return responseMsgSeq;
	}

	public BooleanType isLast() {
		return isLast;
	}

	public ResultType resultType(){
		return resultType;
	}
	
	public int embeddedBlockLength(){
		return embeddedBlockLength;
	}

	public int embeddedTemplateId(){
		return embeddedTemplateId;
	}

	public TemplateType embeddedTemplateType(){
		return TemplateType.get((byte)embeddedTemplateId);
	}
}
