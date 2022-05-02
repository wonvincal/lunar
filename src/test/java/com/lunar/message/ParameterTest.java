package com.lunar.message;

import static org.junit.Assert.assertEquals;

import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableListMultimap;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.TemplateType;

public class ParameterTest {

    @Test
    public void testGetParameterIntoEnum(){
        Parameter parameter = Parameter.of(ParameterType.TEMPLATE_TYPE, TemplateType.ISSUER.value());
        Parameter parameter2 = Parameter.of(ParameterType.EXCHANGE_CODE, "ABC");
        ImmutableListMultimap.Builder<ParameterType, Parameter> builder = ImmutableListMultimap.<ParameterType, Parameter>builder();
        builder.put(parameter.type(), parameter);
        builder.put(parameter2.type(), parameter2);
        ImmutableListMultimap<ParameterType, Parameter> parameters = builder.build();
        Optional<TemplateType> templateType = Parameter.getParameterIfOnlyOneExist(parameters, ParameterType.TEMPLATE_TYPE, TemplateType.class);
        assertEquals(TemplateType.ISSUER, templateType.get());
    }
}
