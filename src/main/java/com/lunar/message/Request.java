package com.lunar.message;

import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableListMultimap;
import com.lunar.entity.Entity;
import com.lunar.entity.Note;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ResultType;
import com.lunar.message.io.sbe.TemplateType;

import it.unimi.dsi.fastutil.objects.Object2BooleanAVLTreeMap;

/**
 * Request can be retried after timeout.
 * 
 * Type of requests
 * 
 * @author Calvin
 *
 */
public class Request extends Message {
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(Request.class);
	private final int senderSinkId;
	
	/**
	 * This is now being populated by the Messenger object.
	 * TODO Creator of the new request should assign a clientKey explicitly!
	 */
	private int clientKey;
	private final RequestType requestType;
	private final ImmutableList<Parameter> parameters;
	private final BooleanType toSend;
	private Optional<Entity> entity;
	private ResultType resultType;
	
	public Request(int senderSinkId, RequestType requestType, ImmutableList<Parameter> parameters, BooleanType toSend){
		this.senderSinkId = senderSinkId;
		this.requestType = requestType;
		this.parameters = parameters;
		this.toSend = toSend;
		this.resultType = ResultType.NULL_VAL;
		this.entity = Optional.empty();
	}

	public int clientKey(){
		return this.clientKey;
	}
	
	public Request clientKey(int clientKey){
		this.clientKey = clientKey;
		return this;
	}

	public BooleanType toSend(){
		return toSend;
	}
	
	public RequestType requestType(){
		return requestType;
	}
	
	public ResultType resultType(){
		return resultType;
	}
	
	public Optional<Entity> entity(){
		return this.entity;
	}
	
	public Request entity(Entity entity){
		this.entity = Optional.of(entity);
		return this;
	}
	
	public Request resultType(ResultType resultType){
		this.resultType = resultType;
		return this;
	}

	public List<Parameter> parameters(){
		return this.parameters;
	}
	
	@Override
	public int senderSinkId(){
		return senderSinkId;
	}

	public static Object2BooleanAVLTreeMap<ParameterType> NOTE_REQUIRED_PARAMETER_TYPES = new Object2BooleanAVLTreeMap<>();
	
	static {
		NOTE_REQUIRED_PARAMETER_TYPES.put(ParameterType.DESCRIPTION, true);
		NOTE_REQUIRED_PARAMETER_TYPES.put(ParameterType.ENTITY_SID, true);
		NOTE_REQUIRED_PARAMETER_TYPES.put(ParameterType.NOTE_SID, true);
		NOTE_REQUIRED_PARAMETER_TYPES.put(ParameterType.IS_ARCHIVED, true);
		NOTE_REQUIRED_PARAMETER_TYPES.put(ParameterType.IS_DELETED, true);
	}

	private static Request create(int senderSinkId, RequestType requestType, ImmutableListMultimap<ParameterType, Parameter> parameters, BooleanType toSend){
		Builder<Parameter> builder = new ImmutableList.Builder<Parameter>();
		Optional<TemplateType> templateType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TEMPLATE_TYPE, TemplateType.class);		
		if (templateType.isPresent()){
			if (templateType.get() == TemplateType.NOTE && (requestType == RequestType.CREATE || requestType == RequestType.UPDATE)){
				// Remove Note specific parameter from Request
				Optional<String> desc = Parameter.getParameterValueString(parameters, ParameterType.DESCRIPTION);
				Optional<Long> entitySid = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.ENTITY_SID);
				Optional<Long> noteSid = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.NOTE_SID);
				Optional<BooleanType> isArchived = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.IS_ARCHIVED, BooleanType.class);
				Optional<BooleanType> isDeleted = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.IS_DELETED, BooleanType.class);
				Note note = Note.of(noteSid, entitySid, isDeleted, isArchived, desc);
				
				// Take only parameters that is not part of NOTE
				builder.addAll(parameters.values().stream().filter(p -> { return !NOTE_REQUIRED_PARAMETER_TYPES.containsKey(p.type());}).iterator());
				return new Request(senderSinkId, requestType, builder.build(), toSend).entity(note);
			}
		}
		return new Request(senderSinkId, requestType, builder.addAll(parameters.values()).build(), toSend);		
	}
	
//	public static Request of(int senderSinkId, RequestSbeDecoder requestSbe, MutableDirectBuffer stringByteBuffer) throws UnsupportedEncodingException{
//		ImmutableListMultimap<ParameterType, Parameter> parameters = RequestDecoder.generateParameterMap(stringByteBuffer, requestSbe);
//		Request request = create(senderSinkId, requestSbe.requestType(), parameters, requestSbe.toSend());
//		if (requestSbe.clientKey() != RequestSbeDecoder.clientKeyNullValue()){
//			request.clientKey(requestSbe.clientKey());
//		}
//		return request;
//	}
//
	public static Request of(int senderSinkId, RequestType requestType, ImmutableListMultimap<ParameterType, Parameter> parameters, BooleanType toSend){
		return new Request(senderSinkId, requestType, new ImmutableList.Builder<Parameter>().addAll(parameters.values()).build(), toSend);		
	}

	public static Request createAndExtractEntityFromParameters(int senderSinkId, RequestType requestType, ImmutableListMultimap<ParameterType, Parameter> parameters, BooleanType toSend){
		return create(senderSinkId, requestType, parameters, toSend);
	}

	public static Request of(int senderSinkId, RequestType requestType, List<Parameter> parameters){
		return new Request(senderSinkId, requestType, new ImmutableList.Builder<Parameter>().addAll(parameters).build(), BooleanType.FALSE);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("clientKey:" + this.clientKey + ", requestType:" + this.requestType.name() + ", resultType:" + resultType.name());
		for (Parameter parameter : parameters){
			builder.append(", ").append(parameter.toString());
		}
		return builder.toString();
	}
}
