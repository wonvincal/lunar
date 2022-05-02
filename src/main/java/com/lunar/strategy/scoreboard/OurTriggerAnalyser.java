package com.lunar.strategy.scoreboard;

import static org.apache.logging.log4j.util.Unbox.box;

import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.message.io.sbe.PricingMode;
import com.lunar.message.io.sbe.PutOrCall;
import com.lunar.order.Tick;
import com.lunar.service.ServiceConstant;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.parameters.GenericWrtParams;
import com.lunar.strategy.scoreboard.stats.SpeedArbHybridStats;

public class OurTriggerAnalyser implements MarketDataUpdateHandler, OurTriggerHandler, MarketOrderHandler, UnderlyingOrderBookUpdateHandler {
    static final Logger LOG = LogManager.getLogger(OurTriggerAnalyser.class);
    
    class OurTriggerOrder extends MarketOrder {
        boolean hasMarketTrade;
    }
    
    final private ScoreBoardSecurityInfo security;    
    final private ScoreBoardCalculator scoreBoardCalculator;
    final private SpeedArbHybridStats stats;
    
    private int mmSpread = Integer.MAX_VALUE;
    
    private OurTriggerOrder ourBuyOrder;
    private OurTriggerOrder ourSellOrder;
    final private OurTriggerExplain ourBuyTriggerExplain = new OurTriggerExplain();

    private long lastMarketTradeBuyTime;
    private int lastMarketBuyPrice;
    
    private boolean hasOurPosition = false;
    private int lowestSellPriceInPosition = Integer.MAX_VALUE;
    private long startTimeBreakEven = 0;
    private long timeToBreakEven = 0;
    private long startTimeProfit = 0;
    private long timeToProfit = 0;
    private int bestBidLevel;
    private int bestBidPrice;
    private long prevSpotInPosition;
    private long currentSpot;
    private long spotAtLowestSellPriceInPosition = 0;

    public OurTriggerAnalyser(final ScoreBoardSecurityInfo security, final ScoreBoardSecurityInfo underlying) {
        this.security = security;
        this.scoreBoardCalculator = security.scoreBoardCalculator();
        this.stats = security.scoreBoard().speedArbHybridStats();
        this.ourBuyOrder = new OurTriggerOrder();
        this.ourSellOrder = new OurTriggerOrder();
        security.registerMdUpdateHandler(this);
        security.registerOurTriggerHandler(this);
        security.registerMarketOrderHandler(this);
        underlying.registerUnderlyingOrderBookUpdateHandler(this);
    }

    private long mmBidSize() {
        return security.marketContext().mmBidSizeThreshold() == 0 ? security.wrtParams().mmBidSize() : security.marketContext().mmBidSizeThreshold(); 
    }
    
    private long mmAskSize() {
        return security.marketContext().mmAskSizeThreshold() == 0 ? security.wrtParams().mmAskSize() : security.marketContext().mmAskSizeThreshold(); 
    }
    
    @Override
    public void onOrderBookUpdated(final Security baseSecurity, final long transactTime, final MarketOrderBook orderBook) throws Exception {
    	int prevBestBidLevel = this.bestBidLevel;
    	processTick(transactTime);
    	if (hasOurPosition) {
    		final Tick bestBid = orderBook.bestBidOrNullIfEmpty();
    		if (bestBid != null) {
    			if (bestBid.price() >= ourBuyOrder.price) {
    				if (timeToBreakEven == 0) {
		    			if (startTimeBreakEven == 0) {
		    				startTimeBreakEven = transactTime;
		    			}
		    			else if (transactTime - startTimeBreakEven >= 5_000_000_000L) {
		    				timeToBreakEven = startTimeBreakEven - ourBuyOrder.orderTime;
		    			}
    				}
        			if (bestBid.price() > ourBuyOrder.price) {
        				if (timeToProfit == 0) {
    		    			if (startTimeProfit == 0) {
    		    				startTimeProfit = transactTime;
    		    			}
    		    			else if (transactTime - startTimeProfit >= 5_000_000_000L) {
    		    				timeToProfit = startTimeProfit - ourBuyOrder.orderTime;
    		    			}
        				}
        			}
        			else {
		    			if (startTimeProfit != 0 && transactTime - startTimeProfit >= 5_000_000_000L) {
		    				timeToProfit = startTimeProfit - ourBuyOrder.orderTime;
		    			}        				
        				startTimeProfit = 0;
        			}
    			}
    			else {
	    			if (startTimeBreakEven != 0 && transactTime - startTimeBreakEven >= 5_000_000_000L) {
	    				timeToBreakEven = startTimeBreakEven - ourBuyOrder.orderTime;
	    			}
	    			if (startTimeProfit != 0 && transactTime - startTimeProfit >= 5_000_000_000L) {
	    				timeToProfit = startTimeProfit - ourBuyOrder.orderTime;
	    			}
    				startTimeBreakEven = 0;
    				startTimeProfit = 0;    				
    			}
    		}
    		if (prevBestBidLevel != bestBidLevel) {
    			updateMtmTheoreticalPenalty();
    			updateMtmScores(transactTime, orderBook.triggerInfo().triggerSeqNum());
    		}
    	}
    }
    
    @Override
    public void onTradeReceived(final Security baseSecurity, final long timestamp, final MarketTrade trade) throws Exception {
    }

    @Override
    public void onOurTriggerBuyReceived(final long timestamp, final int price, final OurTriggerExplain ourTriggerExplain, final boolean isAdditionalTrigger, final long triggerSeqNum) {
        hasOurPosition = true;
        if (!isAdditionalTrigger) {
            lowestSellPriceInPosition = Integer.MAX_VALUE;
            startTimeBreakEven = 0;
            timeToBreakEven = 0;
            startTimeProfit = 0;
            timeToProfit = 0;
            prevSpotInPosition = 0;
        	
            ourBuyOrder.triggerSeqNum = triggerSeqNum;
            ourBuyOrder.orderTime = timestamp;
            ourBuyOrder.side = ASK;
            ourBuyOrder.price = price;
            ourBuyOrder.tickLevel = security.spreadTable().priceToTick(price);
            ourBuyOrder.quantity = 1;
            ourBuyOrder.grossPrice = price;
            ourBuyOrder.spread_3L = mmSpread != Integer.MAX_VALUE || mmSpread != 0 ? mmSpread * 1000 : Long.MAX_VALUE;
            ourBuyOrder.hasMarketTrade = price == lastMarketBuyPrice ? timestamp - lastMarketTradeBuyTime <= ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS : false;
            
            ourBuyTriggerExplain.copyFrom(ourTriggerExplain);
        }
    }
    
    @Override
    public void onOurTriggerSellReceived(final long timestamp, final int price, final OurTriggerExplain ourTriggerExplain, final long triggerSeqNum) {
        hasOurPosition = false;
        ourSellOrder.triggerSeqNum = triggerSeqNum;
        ourSellOrder.orderTime = timestamp;
        ourSellOrder.side = BID;
        ourSellOrder.tickLevel = security.spreadTable().priceToTick(price);
        ourSellOrder.quantity = 1;
        ourSellOrder.grossPrice = price;
        ourSellOrder.spread_3L = mmSpread != Integer.MAX_VALUE || mmSpread != 0 ? mmSpread * 1000 : Long.MAX_VALUE;
        final long spotAtSell;
        if (lowestSellPriceInPosition < price) {
        	ourSellOrder.price = lowestSellPriceInPosition;
        	spotAtSell = spotAtLowestSellPriceInPosition;
        }
        else {
            ourSellOrder.price = price;
        	spotAtSell = getSpotForTheoreticalPenalty();
        }
        ourSellOrder.tickLevel = security.spreadTable().priceToTick(ourSellOrder.price);
        
        final int theoreticalPenalty = calcTheoreticalPenalty(ourBuyOrder.price, this.ourBuyTriggerExplain, price, spotAtSell);
        if (ourBuyOrder.hasMarketTrade) {
            stats.incrementOurTheoreticalPenalty(theoreticalPenalty);
            stats.setOurMtmTheoreticalPenalty(stats.getOurTheoreticalPenalty());
        }
        updateScores(timestamp, triggerSeqNum);
        LOG.info("Found our triggered trade: secCode {}, ourScore {}, buyPrice {}, sellPrice {}, realSellPrice {}, theoreticalPenalty {}, buySpread {}, sellSpread {}, " +
                "hasPunterTrigger {}, timeToBreakEven {}, timeToProfit {}, buyDateTime {}, buyTriggerSeqNum {}, sellDateTime {}, sellTriggerSeqNum {}",
               security.code(), box(stats.getOurScore()), box(ourBuyOrder.price), box(ourSellOrder.price), box(price), box(theoreticalPenalty), box(ourBuyOrder.spread_3L), box(ourSellOrder.spread_3L),                
               box(ourBuyOrder.hasMarketTrade), box(timeToBreakEven), box(timeToProfit),
               box(ourBuyOrder.orderTime), box(ourBuyOrder.triggerSeqNum), box(ourSellOrder.orderTime), box(ourSellOrder.triggerSeqNum));
    }

	@Override
	public void onBuyTrade(long nanoOfDay, MarketOrder marketOrder) {
		lastMarketTradeBuyTime = nanoOfDay;
		lastMarketBuyPrice = marketOrder.price;
        if (hasOurPosition && !ourBuyOrder.hasMarketTrade && ourBuyOrder.price == lastMarketBuyPrice && lastMarketTradeBuyTime - ourBuyOrder.orderTime <= ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS) {
            ourBuyOrder.hasMarketTrade = true;
        }
	}

	@Override
	public void onSellTrade(long nanoOfDay, MarketOrder marketOrder, MarketOrder matchedBuyOrder) {
        if (this.hasOurPosition && this.lowestSellPriceInPosition > marketOrder.price) {
            this.lowestSellPriceInPosition = marketOrder.price;
            this.spotAtLowestSellPriceInPosition = getSpotForTheoreticalPenalty();;
        }
	}
	
	@Override
	public void onUnderlyingOrderBookUpdated(long nanoOfDay, UnderlyingOrderBook underlyingOrderBook) {
		final PricingMode pricingMode = this.hasOurPosition ? ourBuyTriggerExplain.pricingMode : ((GenericWrtParams)(security.wrtParams())).pricingMode();
		if (this.hasOurPosition) {
		    prevSpotInPosition = currentSpot;
		}
		currentSpot = pricingMode == PricingMode.MID ? underlyingOrderBook.midPrice : underlyingOrderBook.weightedAverage;
		if (this.hasOurPosition && currentSpot != prevSpotInPosition) {
			updateMtmTheoreticalPenalty();
		}		
	}

	
    private void processTick(final long nanoOfDay) {
        int mmBidLevel = 0;
        bestBidLevel = 0;
        bestBidPrice = 0;
        if (!security.orderBook().bidSide().isEmpty()) {
            Tick tick = security.orderBook().bidSide().bestOrNullIfEmpty();
            bestBidLevel = tick.tickLevel();
            bestBidPrice = tick.price();
            if (tick.qty() >= mmBidSize()) {
                mmBidLevel = tick.tickLevel();
            }
            else {
                final Iterator<Tick> iterator = security.orderBook().bidSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= mmBidSize()) {
                        mmBidLevel = tick.tickLevel();
                        break;
                    }
                }
            }
        }
        if (mmBidLevel > 0 && !security.orderBook().askSide().isEmpty()) {
            Tick tick = security.orderBook().askSide().bestOrNullIfEmpty();
            if (tick.qty() >= mmAskSize()) {
                mmSpread = tick.tickLevel() - mmBidLevel;
            }
            else {
                final Iterator<Tick> iterator = security.orderBook().askSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= mmAskSize()) {
                        mmSpread = tick.tickLevel() - mmBidLevel;
                        break;
                    }
                }
            }
        }
    }

    private void updateScores(final long nanoOfDay, final long triggerSeqNum) {
        if (timeToBreakEven == 0 && ourSellOrder.tickLevel - ourBuyOrder.tickLevel >= 0) {
        	if (startTimeBreakEven != 0) {
        		timeToBreakEven = startTimeBreakEven - ourBuyOrder.orderTime;
        	}
        	else {
        		timeToBreakEven = ourSellOrder.orderTime - ourBuyOrder.orderTime;
        	}
        }
        if (timeToProfit == 0 && ourSellOrder.tickLevel - ourBuyOrder.tickLevel > 0) {
        	if (startTimeProfit != 0) {
        		timeToProfit = startTimeProfit - ourBuyOrder.orderTime;
        	}
        	else {
        		timeToProfit = ourSellOrder.orderTime - ourBuyOrder.orderTime;
        	}
        }
        updateScores(ourBuyOrder, ourSellOrder, timeToBreakEven, timeToProfit, nanoOfDay, triggerSeqNum);
    }

    private void updateScores(final OurTriggerOrder buyOrder, final OurTriggerOrder sellOrder, final long timeToBreakEven, final long timeToProfit, final long nanoOfDay, final long triggerSeqNum) {
        final long holdingTime = sellOrder.orderTime - buyOrder.orderTime;
        final int pnlTicks = sellOrder.tickLevel - buyOrder.tickLevel;        
        int currentScore = stats.getOurScore();
        int currentScoreWithPunter = stats.getOurScoreWithPunter();

        final int scoreToAdd;
        final int scoreToAddWithPunter;
        if (!buyOrder.hasMarketTrade && pnlTicks > 0) {
            scoreToAdd = scoreBoardCalculator.calcScoreFromPnlTicks(pnlTicks, timeToBreakEven, timeToProfit, holdingTime, buyOrder.spread_3L);
            scoreToAddWithPunter = 0;
        }
        else {            
            scoreToAdd = scoreBoardCalculator.calcScoreFromPnlTicks(pnlTicks, timeToBreakEven, timeToProfit, holdingTime, buyOrder.spread_3L);
            scoreToAddWithPunter = scoreToAdd;
            stats.incrementOurPnlTicksWithPunter(pnlTicks);
        }
        
        currentScore += scoreToAdd;
        currentScoreWithPunter += scoreToAddWithPunter;
        stats.incrementOurPnlTicks(pnlTicks);        
        stats.setOurScore(currentScore);
        stats.setOurScoreWithPunter(currentScoreWithPunter);
        stats.setOurMtmScore(currentScore);
        stats.setOurMtmScoreWithPunter(currentScoreWithPunter);
        scoreBoardCalculator.updateScore(nanoOfDay, triggerSeqNum);
    }
    
    private void updateMtmScores(final long nanoOfDay, final long triggerSeqNum) {
    	final long timeToBreakEven;
        if (this.timeToBreakEven == 0 && bestBidLevel - ourBuyOrder.tickLevel >= 0) {
        	timeToBreakEven = nanoOfDay - ourBuyOrder.orderTime; 
        }
        else {
        	timeToBreakEven = this.timeToBreakEven;
        }
        final long timeToProfit;
        if (this.timeToProfit == 0 && bestBidLevel - ourBuyOrder.tickLevel > 0) {
        	timeToProfit = ourSellOrder.orderTime - ourBuyOrder.orderTime; 
        }
        else {
        	timeToProfit = this.timeToProfit;
        }
        updateMtmScores(ourBuyOrder, bestBidLevel, timeToBreakEven, timeToProfit, nanoOfDay, triggerSeqNum);    	
    }
    
    private void updateMtmScores(final OurTriggerOrder buyOrder, final int bestBidLevel, final long timeToBreakEven, final long timeToProfit, final long nanoOfDay, final long triggerSeqNum) {
        final long holdingTime = nanoOfDay - buyOrder.orderTime;
        final int pnlTicks = bestBidLevel - buyOrder.tickLevel;        
        int currentScore = stats.getOurScore();
        int currentScoreWithPunter = stats.getOurScoreWithPunter();

        final int scoreToAdd;
        final int scoreToAddWithPunter;
        if (!buyOrder.hasMarketTrade && pnlTicks > 0) {
            scoreToAdd = scoreBoardCalculator.calcScoreFromPnlTicks(pnlTicks, timeToBreakEven, timeToProfit, holdingTime, buyOrder.spread_3L);
            scoreToAddWithPunter = 0;
        }
        else {            
            scoreToAdd = scoreBoardCalculator.calcScoreFromPnlTicks(pnlTicks, timeToBreakEven, timeToProfit, holdingTime, buyOrder.spread_3L);
            scoreToAddWithPunter = scoreToAdd;
        }
        
        currentScore += scoreToAdd;
        currentScoreWithPunter += scoreToAddWithPunter;
        
        final int prevMtmScore = stats.getOurMtmScore();
        final int prevMtmScoreWithPunter = stats.getOurMtmScoreWithPunter();
        stats.setOurMtmScore(currentScore);
        stats.setOurMtmScoreWithPunter(currentScoreWithPunter);
        if (prevMtmScore != stats.getOurMtmScore() || prevMtmScoreWithPunter != stats.getOurMtmScoreWithPunter()) {
        	scoreBoardCalculator.updateScore(nanoOfDay, triggerSeqNum);
        }
    }
    
    private void updateMtmTheoreticalPenalty() {
    	if (this.hasOurPosition && this.bestBidLevel != 0 && this.ourBuyOrder.hasMarketTrade) {
    		final long spot = getSpotForTheoreticalPenalty();
    		final int theoreticalPenalty = calcTheoreticalPenalty(this.ourBuyOrder.price, this.ourBuyTriggerExplain, this.bestBidPrice, spot);    		
    		stats.setOurMtmTheoreticalPenalty(stats.getOurTheoreticalPenalty() + theoreticalPenalty);
    	}
    }

    private int calcTheoreticalPenalty(final int buyPrice, final OurTriggerExplain buyExplain, final int sellPrice, final long sellSpot) {
        final long spotIncrease = sellSpot - buyExplain.undSpot;
        final float deltaC = (float)buyExplain.delta / (security.convRatio() * 100.0f);
        final long theoreticalPnl = (long)(spotIncrease * deltaC);
        final int theoreticalBid = buyPrice + (int)(theoreticalPnl / ServiceConstant.WEIGHTED_AVERAGE_SCALE);
        final int theoreticalBidLevel = getFloorTickLevel(theoreticalBid, security.spreadTable().priceToTickSize(buyPrice));     
        final int sellLevel = security.spreadTable().priceToTick(sellPrice);
        return theoreticalBidLevel - sellLevel;
    }
    
    private int getFloorTickLevel(final int price, int estimateTickSize) {
        int guessedLevel = SpreadTable.SPREAD_TABLE_MIN_LEVEL + (price - security.spreadTable().tickToPrice(SpreadTable.SPREAD_TABLE_MIN_LEVEL)) / estimateTickSize;
        int guessedPrice = security.spreadTable().tickToPrice(guessedLevel);
        estimateTickSize = guessedPrice <= price ? security.spreadTable().priceToTickSize(guessedPrice) : guessedPrice - security.spreadTable().tickToPrice(guessedLevel -1);
        int levelsToAdd = (price - guessedPrice) / estimateTickSize;        
        while (levelsToAdd != 0) {
            guessedLevel += levelsToAdd;
            if (guessedLevel >= security.spreadTable().maxLevel()) {
                guessedLevel = security.spreadTable().maxLevel();
            }
            guessedPrice = security.spreadTable().tickToPrice(guessedLevel);
            estimateTickSize = guessedPrice <= price ? security.spreadTable().priceToTickSize(guessedPrice) : guessedPrice - security.spreadTable().tickToPrice(guessedLevel -1);
            levelsToAdd = (price - guessedPrice) / estimateTickSize;
        }
        if (guessedPrice > price) {
            guessedLevel--;
        }
        return guessedLevel;
    }
    
    private long getSpotForTheoreticalPenalty() {
        return this.prevSpotInPosition == 0 ? this.currentSpot : (security.putOrCall() == PutOrCall.CALL ? Math.max(this.prevSpotInPosition, this.currentSpot) : Math.min(this.prevSpotInPosition, this.currentSpot));
    }

}
