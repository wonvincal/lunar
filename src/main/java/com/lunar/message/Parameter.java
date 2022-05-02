package com.lunar.message;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.lunar.message.io.sbe.CommandSbeEncoder;
import com.lunar.message.io.sbe.DataType;
import com.lunar.message.io.sbe.LineHandlerActionType;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;

public class Parameter {
	private final ParameterType type;
	private final String value;
	private final Long valueLong;
	
	public Parameter(ParameterType type, String value, Long valueLong){
		this.type = type;
		this.value = value;
		this.valueLong = valueLong;
	}
	
	public ParameterType type(){
		return this.type;
	}
	
	public String value(){
		return this.value;
	}
	
	public Long valueLong(){
		return this.valueLong;
	}
	
	public boolean isLongValue(){
		return (valueLong != null);
	}

	public static Parameter of(ParameterType type, String value){
		return of(type, value, false);
	}
	
	public static Parameter of(ParameterType type, String value, boolean ignoreSizeCheck){
		if (value == null){
			throw new IllegalArgumentException("value of parameter type (" + type.name() + ") must not be null");
		}
		if (!ignoreSizeCheck && value.length() > CommandSbeEncoder.ParametersEncoder.parameterValueLength()){
			throw new IllegalArgumentException("value(" + value + ") of parameter type (" + type.name() + "), its length must be less than " + CommandSbeEncoder.ParametersEncoder.parameterValueLength());
		}
		return new Parameter(type, value, null);
	}
	
	public static Parameter of(DataType value){
		return new Parameter(ParameterType.DATA_TYPE, null, Long.valueOf(value.value()));
	}
	
	public static Parameter of(TemplateType value){
		return new Parameter(ParameterType.TEMPLATE_TYPE, null, Long.valueOf(value.value()));
	}
	
	public static Parameter of(ServiceType value){
		return new Parameter(ParameterType.SERVICE_TYPE, null, Long.valueOf(value.value()));
	}
	
	public static Parameter of(LineHandlerActionType value){
		return new Parameter(ParameterType.LINE_HANDLER_ACTION_TYPE, null, Long.valueOf(value.value()));
	}
	
	public static Parameter of(ParameterType type, long value){
		return new Parameter(type, null, value);
	}

	public static ImmutableList<Parameter> NULL_LIST = ImmutableList.of();
	
	public static ImmutableMap<ParameterType, Parameter> NULL_MAP = ImmutableMap.of();

	@Override
	public String toString() {
		return "type:" + type.name() + ", value:" + (this.isLongValue() ? this.valueLong : this.value);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		result = prime * result + ((valueLong == null) ? 0 : valueLong.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Parameter other = (Parameter) obj;
		if (type != other.type)
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		if (valueLong == null) {
			if (other.valueLong != null)
				return false;
		} else if (!valueLong.equals(other.valueLong))
			return false;
		return true;
	}
	
	/**
	 * 
	 * @param parameters
	 * @param parameterType
	 * @param enumClazz
	 * @return Empty if there is zero or more than one parameters with input ParameterType
	 */
	public static <T> Optional<T> getParameterIfOnlyOneExist(ImmutableListMultimap<ParameterType, Parameter> parameters, ParameterType parameterType, Class<T> enumClazz){
		ImmutableList<Parameter> items = parameters.get(parameterType);
		if (items.size() != 1){
			return Optional.empty();
		}
		if (!enumClazz.isEnum()){
			throw new IllegalArgumentException("Must be enum");
		}
		Method method;
		try {
			method = enumClazz.getMethod("get", byte.class);
			Object o = method.invoke(null, items.get(0).valueLong().byteValue());
			return Optional.of(enumClazz.cast(o));
		}
		catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			return Optional.empty();
		}
	}

	public static Optional<String> getParameterValueString(ImmutableListMultimap<ParameterType, Parameter> parameters, ParameterType parameterType){
		ImmutableList<Parameter> items = parameters.get(parameterType);
		if (items.size() != 1){
			return Optional.empty();
		}
		return Optional.of(items.get(0).value());
	}

	public static Optional<Long> getParameterIfOnlyOneExist(ImmutableListMultimap<ParameterType, Parameter> parameters, ParameterType parameterType){
		ImmutableList<Parameter> items = parameters.get(parameterType);
		if (items.size() != 1){
			return Optional.empty();
		}
		return Optional.of(items.get(0).valueLong());
	}
}
