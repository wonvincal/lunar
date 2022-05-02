package com.lunar.strategy;

import java.util.Collection;

import com.lunar.entity.StrategyType;
import com.lunar.message.io.sbe.RequestSbeDecoder.ParametersDecoder;
import com.lunar.message.io.sbe.StrategyIssuerParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyIssuerUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyUndParamsSbeDecoder;
import com.lunar.message.io.sbe.StrategyWrtParamsSbeDecoder;
import com.lunar.strategy.parameters.IssuerInputParams;
import com.lunar.strategy.parameters.IssuerUndInputParams;
import com.lunar.strategy.parameters.ParamsSbeEncodable;
import com.lunar.strategy.parameters.StrategyTypeInputParams;
import com.lunar.strategy.parameters.UndInputParams;
import com.lunar.strategy.parameters.WrtInputParams;

public interface StrategySchema {
    void populateSchema(final StrategyType strategyType);

    void handleStratTypeParamUpdateRequest(final ParametersDecoder parameters, final StrategyTypeInputParams strategyParams);
    void handleStratUndParamUpdateRequest(final ParametersDecoder parameters, final UndInputParams strategyUndParams);
    void handleStratIssuerUndParamUpdateRequest(final ParametersDecoder parameters, final IssuerUndInputParams strategyIssuerUndParams);
    void handleStratIssuerParamUpdateRequest(final ParametersDecoder parameters, final IssuerInputParams strategyIssuerParams);
    void handleStratWrtParamUpdateRequest(final ParametersDecoder parameters, final WrtInputParams strategyWrtParams);
    void handleStratWrtParamUpdateRequest(final ParametersDecoder parameters, final Collection<WrtInputParams> strategyWrtParamsList);
    
    void handleStrategyTypeParamsSbe(final StrategyParamsSbeDecoder sbe, final ParamsSbeEncodable strategyParams);
    void handleStrategyUndParamsSbe(final StrategyUndParamsSbeDecoder sbe, final ParamsSbeEncodable strategyUndParams);
    void handleStrategyIssuerParamsSbe(final StrategyIssuerParamsSbeDecoder sbe, final ParamsSbeEncodable strategyIssuerParams);
    void handleStrategyWrtParamsSbe(final StrategyWrtParamsSbeDecoder sbe, final ParamsSbeEncodable strategyWrtParams);
    void handleStrategyIssuerUndParamsSbe(final StrategyIssuerUndParamsSbeDecoder sbe, final ParamsSbeEncodable strategyIssuerUndParams);

}
