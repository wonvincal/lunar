package com.lunar.message;

import com.google.common.collect.ImmutableList;

public class Parameters {
	public static ImmutableList<Parameter> listOf(Parameter... parameters){ 
		return new ImmutableList.Builder<Parameter>().add(parameters).build();
	}
}
