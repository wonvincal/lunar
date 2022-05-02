package com.lunar.strategy.scoreboard;

import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.strategy.StrategyFactory;
import com.lunar.strategy.StrategyInfoSender;
import com.lunar.strategy.StrategyOrderService;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.scoreboard.StrategySwitchController.SwitchControlHandler;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridFactory;
import com.lunar.strategy.speedarbhybrid.SpeedArbHybridTestHelper;

public class ScoreBoardTestHelper extends SpeedArbHybridTestHelper {
    private ScoreBoardManager scoreBoardManager; 

    public ScoreBoardTestHelper(final int expectedNumWrts, final int expectedNumUnds, final int expectedNumWrtsPerUnd) {
        super(expectedNumWrts, expectedNumUnds, expectedNumWrtsPerUnd);        
    }
    
    public ScoreBoardManager scoreBoardManager() {
        return scoreBoardManager;
    }

    protected StrategyFactory strategyFactory() {
        return new SpeedArbHybridFactory();
    }
    
    @Override
    public void setupStrategyManager(final StrategyOrderService orderService, final StrategyInfoSender paramsUpdateHandler) throws Exception {
        setupStrategyManager(orderService, createParamsUpdateHandler(), StrategySwitchController.NULL_SWITCH_CONTROL_HANDLER);
    }

    public void setupStrategyManager(final StrategyOrderService orderService, final SwitchControlHandler switchControlHandler) throws Exception {
        setupStrategyManager(orderService, createParamsUpdateHandler(), switchControlHandler);
    }

    public void setupStrategyManager(final StrategyOrderService orderService, final StrategyInfoSender paramsUpdateHandler, final SwitchControlHandler switchControlHandler) throws Exception {
        super.setupStrategyManager(orderService, paramsUpdateHandler);
        scoreBoardManager = new ScoreBoardManager(this.securities, this.issuers, this.strategyManager, StrategyInfoSender.NULL_PARAMS_SENDER, switchControlHandler);
    }
    
    @Override
    protected StrategySecurity createSecurity(long sid, SecurityType securityType, String code, long undSecSid, PutOrCall putOrCall, OptionStyle optionStyle, int strikePrice, int convRatio, int issuerSid, int lotSize) {
        final ScoreBoardSecurityInfo newSecurity = new ScoreBoardSecurityInfo(sid, 
                securityType, 
                code, 
                0,
                undSecSid,
                putOrCall,
                optionStyle,
                strikePrice,
                convRatio,
                issuerSid,
                lotSize,
                true,
                SpreadTableBuilder.get(securityType));
        return newSecurity;
    }

    @Override
    public void createStrategies() throws Exception {
        super.createStrategies();
        scoreBoardManager.onEntitiesLoaded();
        scoreBoardManager.setupScoreBoards();
    }
    
    @Override
    protected StrategyInfoSender createParamsUpdateHandler() {
        return new ScoreBoardStrategyInfoSender(securities);
    }

}
