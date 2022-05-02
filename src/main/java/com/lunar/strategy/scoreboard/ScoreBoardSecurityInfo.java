package com.lunar.strategy.scoreboard;

import com.lunar.fsm.cutloss.CutLossFsm;
import com.lunar.fsm.cutloss.MarketContext;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketStats;
import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.OptionStyle;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.message.io.sbe.SecurityType;
import com.lunar.message.io.sbe.TradeSbeDecoder;
import com.lunar.strategy.StrategySecurity;
import com.lunar.strategy.parameters.UndInputParams;
import com.lunar.strategy.parameters.WrtInputParams;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class ScoreBoardSecurityInfo extends StrategySecurity {
    private static final int BOOK_DEPTH = 10;

    final private MarketStats marketStats;
    final private ScoreBoard scoreBoard;
    private ScoreBoardCalculator scoreBoardCalculator;
    private StrategySwitchController strategySwitchController;
    private UndInputParams undParams;
    private WrtInputParams wrtParams;
    private MarketOrderHandler marketOrderHandler;
    private VolatilityChangedHandler volatilityChangedHandler;
    private UnderlyingOrderBookUpdateHandler underlyingOrderBookUpdateHandler;
    private OurTriggerHandler ourTriggerHandler;
    private ScoreBoardUpdateHandler scoreBoardUpdateHandler;
    private long realPosition; // we need somewhere to store the actual position since the "position" in the base class is used by the mock speedarb strategy therefore is not real
    private MarketMakingChangeTracker mmChangeTracker;
    private final ObjectArrayList<CutLossFsm> cutLossFsms;
    private MarketContext marketContext;
    
    public ScoreBoardSecurityInfo(final long sid, final SecurityType secType, final String code, final int exchangeSid, final long undSecSid,
            final PutOrCall putOrCall, final OptionStyle optionStyle, final int strikePrice, final int convRatio,
            final int issuerSid, final int lotSize, final boolean isAlgo, SpreadTable spreadTable) {
        super(sid, secType, code, exchangeSid, undSecSid,
                putOrCall, optionStyle, strikePrice, convRatio,
                issuerSid, lotSize, isAlgo, spreadTable,
                MarketOrderBook.of(sid, BOOK_DEPTH, spreadTable, Integer.MIN_VALUE, Integer.MIN_VALUE));            
        this.marketStats = MarketStats.of();
        if (this.securityType().equals(SecurityType.WARRANT)) {
            this.scoreBoard = ScoreBoard.of(this);
        }
        else {
            this.scoreBoard = null;
        }
        // Fails if more than two fsms.
        CutLossFsm[] items = new CutLossFsm[2];
        this.cutLossFsms = ObjectArrayList.wrap(items, 0);
    }
    
    public long realPosition() {
        return realPosition;
    }
    
    public void realPosition(final long position) {
        realPosition = position;
    }
    
    public long updateRealPosition(final long quantity) {
        realPosition += quantity;
        return realPosition;
    }
    
    public MarketStats marketStats() {
        return this.marketStats;
    }
    
    public ScoreBoard scoreBoard() {
        return this.scoreBoard;
    }
    
    public MarketMakingChangeTracker marketMakingChangeTracker() {
        return this.mmChangeTracker;
    }

    public void marketMakingChangeTracker(final MarketMakingChangeTracker mmChangeTracker) {
        this.mmChangeTracker = mmChangeTracker;
    }

    public ObjectArrayList<CutLossFsm> cutLossFsms() {
        return this.cutLossFsms;
    }

    public ScoreBoardSecurityInfo addCutLossFsm(final CutLossFsm cutLossFsm) {
        this.cutLossFsms.add(cutLossFsm);
        return this;
    }
    
    public ScoreBoardCalculator scoreBoardCalculator() {
        return this.scoreBoardCalculator;
    }
    
    public void scoreBoardCalculator(final ScoreBoardCalculator scoreBoardCalculator) {
        this.scoreBoardCalculator = scoreBoardCalculator;
    }
    
    public StrategySwitchController strategySwitchController() {
        return this.strategySwitchController;
    }
    
    public void strategySwitchController(final StrategySwitchController strategySwitchController) {
        this.strategySwitchController = strategySwitchController;        
    }
    
    public UndInputParams undParams() {
        return this.undParams;
    }
    public void undParams(final UndInputParams undParams) {
        this.undParams = undParams;
    }
    
    public WrtInputParams wrtParams() {
        return this.wrtParams;
    }
    public void wrtParams(final WrtInputParams wrtParams) {
        this.wrtParams = wrtParams;
        wrtParams.allowAdditionalBuy(true);
    }
    
    public MarketContext marketContext() {
        return this.marketContext;
    }
    public void marketContext(final MarketContext marketContext) {
        this.marketContext = marketContext;
    }
    
    public MarketOrderHandler marketOrderHandler() {
        return this.marketOrderHandler;
    }
    
    public void registerMarketOrderHandler(final MarketOrderHandler handler) {
        if (this.marketOrderHandler == null) {
            this.marketOrderHandler = handler;
        }
        else if (this.marketOrderHandler != handler) {
            final MultiCompositeMarketOrderHandler multipleHandler;
            if (this.marketOrderHandler instanceof MultiCompositeMarketOrderHandler) {
                multipleHandler = (MultiCompositeMarketOrderHandler)this.marketOrderHandler;               
            }
            else {
                multipleHandler = new MultiCompositeMarketOrderHandler();
                multipleHandler.registerMarketOrderHandler(this.marketOrderHandler);
                this.marketOrderHandler = multipleHandler;
            }
            multipleHandler.registerMarketOrderHandler(handler);
        }
    }
    
    public void unregisterMarketOrderHandler(final MarketOrderHandler handler) {
        if (this.marketOrderHandler == handler) {
            this.marketOrderHandler = null;
        }
        else if (this.marketOrderHandler instanceof MultiCompositeMarketOrderHandler) {
            final MultiCompositeMarketOrderHandler multipleHandler = (MultiCompositeMarketOrderHandler)this.marketOrderHandler;
            multipleHandler.unregisterMarketOrderHandler(handler);
        }
    }

    public VolatilityChangedHandler volatilityChangedHandler() {
        return this.volatilityChangedHandler;
    }
    
    public void registerVolatilityChangedHandler(final VolatilityChangedHandler handler) {
        if (this.volatilityChangedHandler == null) {
            this.volatilityChangedHandler = handler;
        }
        else if (this.volatilityChangedHandler != handler) {
            final MultiCompositeVolatilityChangedHandler multipleHandler;
            if (this.volatilityChangedHandler instanceof MultiCompositeVolatilityChangedHandler) {
                multipleHandler = (MultiCompositeVolatilityChangedHandler)this.volatilityChangedHandler;               
            }
            else {
                multipleHandler = new MultiCompositeVolatilityChangedHandler();
                multipleHandler.registerDropVolHandler(this.volatilityChangedHandler);
                this.volatilityChangedHandler = multipleHandler;
            }
            multipleHandler.registerDropVolHandler(handler);
        }
    }
    
    public void unregisterVolatilityChangedHandler(final VolatilityChangedHandler handler) {
        if (this.volatilityChangedHandler == handler) {
            this.volatilityChangedHandler = null;
        }
        else if (this.volatilityChangedHandler instanceof MultiCompositeVolatilityChangedHandler) {
            final MultiCompositeVolatilityChangedHandler multipleHandler = (MultiCompositeVolatilityChangedHandler)this.volatilityChangedHandler;
            multipleHandler.unregisterDropVolHandler(handler);
        }
    }
    
    public UnderlyingOrderBookUpdateHandler underlyingOrderBookUpdateHandler() {
        return this.underlyingOrderBookUpdateHandler;
    }
    
    public void registerUnderlyingOrderBookUpdateHandler(final UnderlyingOrderBookUpdateHandler handler) {
        if (this.underlyingOrderBookUpdateHandler == null) {
            this.underlyingOrderBookUpdateHandler = handler;
        }
        else if (this.underlyingOrderBookUpdateHandler != handler) {
            final MultiCompositeUnderlyingOrderBookUpdateHandler multipleHandler;
            if (this.underlyingOrderBookUpdateHandler instanceof MultiCompositeUnderlyingOrderBookUpdateHandler) {
                multipleHandler = (MultiCompositeUnderlyingOrderBookUpdateHandler)this.underlyingOrderBookUpdateHandler;               
            }
            else {
                multipleHandler = new MultiCompositeUnderlyingOrderBookUpdateHandler();
                multipleHandler.registerUnderlyingOrderBookUpdateHandler(this.underlyingOrderBookUpdateHandler);
                this.underlyingOrderBookUpdateHandler = multipleHandler;
            }
            multipleHandler.registerUnderlyingOrderBookUpdateHandler(handler);
        }
    }
    
    public void unregisterUnderlyingSpreadChangeHandler(final UnderlyingOrderBookUpdateHandler handler) {
        if (this.underlyingOrderBookUpdateHandler == handler) {
            this.underlyingOrderBookUpdateHandler = null;
        }
        else if (this.underlyingOrderBookUpdateHandler instanceof MultiCompositeUnderlyingOrderBookUpdateHandler) {
            final MultiCompositeUnderlyingOrderBookUpdateHandler multipleHandler = (MultiCompositeUnderlyingOrderBookUpdateHandler)this.underlyingOrderBookUpdateHandler;
            multipleHandler.unregisterUnderlyingOrderBookUpdateHandler(handler);
        }
    }

    public void onOwnSecTrade(TradeSbeDecoder trade){
    	CutLossFsm[] fsms = this.cutLossFsms.elements();
    	for (int i = 0; i < this.cutLossFsms.size(); i++){
    		fsms[i].onOwnSecTrade(trade);
    	}
    }
    
    public ScoreBoardUpdateHandler scoreBoardUpdateHandler() {
        return this.scoreBoardUpdateHandler;
    }
    
    public void registerScoreBoardUpdateHandler(final ScoreBoardUpdateHandler handler) {
        if (this.scoreBoardUpdateHandler == null) {
            this.scoreBoardUpdateHandler = handler;
        }
        else if (this.scoreBoardUpdateHandler != handler) {
            final MultiCompositeScoreBoardUpdateHandler multipleHandler;
            if (this.scoreBoardUpdateHandler instanceof MultiCompositeScoreBoardUpdateHandler) {
                multipleHandler = (MultiCompositeScoreBoardUpdateHandler)this.scoreBoardUpdateHandler;               
            }
            else {
                multipleHandler = new MultiCompositeScoreBoardUpdateHandler();
                multipleHandler.registerScoreUpdateHandler(this.scoreBoardUpdateHandler);
                this.scoreBoardUpdateHandler = multipleHandler;
            }
            multipleHandler.registerScoreUpdateHandler(handler);
        }
    }
    
    public void unregisterScoreBoardUpdateHandler(final ScoreBoardUpdateHandler handler) {
        if (this.scoreBoardUpdateHandler == handler) {
            this.scoreBoardUpdateHandler = null;
        }
        else if (this.scoreBoardUpdateHandler instanceof MultiCompositeScoreBoardUpdateHandler) {
            final MultiCompositeScoreBoardUpdateHandler multipleHandler = (MultiCompositeScoreBoardUpdateHandler)this.scoreBoardUpdateHandler;
            multipleHandler.unregisterScoreUpdateHandler(handler);
        }
    }
    
    public OurTriggerHandler ourTriggerHandler() {
        return this.ourTriggerHandler;
    }
    
    public void registerOurTriggerHandler(final OurTriggerHandler handler) {
        if (this.ourTriggerHandler == null) {
            this.ourTriggerHandler = handler;
        }
        else if (this.ourTriggerHandler != handler) {
            final MultiCompositeOurTriggerHandler multipleHandler;
            if (this.ourTriggerHandler instanceof MultiCompositeOurTriggerHandler) {
                multipleHandler = (MultiCompositeOurTriggerHandler)this.ourTriggerHandler;               
            }
            else {
                multipleHandler = new MultiCompositeOurTriggerHandler();
                multipleHandler.registerOurTriggerHandler(this.ourTriggerHandler);
                this.ourTriggerHandler = multipleHandler;
            }
            multipleHandler.registerOurTriggerHandler(handler);
        }
    }
    
    public void unregisterOurTriggerHandler(final OurTriggerHandler handler) {
        if (this.ourTriggerHandler == handler) {
            this.ourTriggerHandler = null;
        }
        else if (this.ourTriggerHandler instanceof MultiCompositeOurTriggerHandler) {
            final MultiCompositeOurTriggerHandler multipleHandler = (MultiCompositeOurTriggerHandler)this.ourTriggerHandler;
            multipleHandler.unregisterOurTriggerHandler(handler);
        }
    }

}

