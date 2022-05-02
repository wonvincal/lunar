package com.lunar.strategy.scoreboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.Security;
import com.lunar.marketdata.MarketOrderBook;
import com.lunar.marketdata.MarketTrade;
import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.order.Tick;
import com.lunar.strategy.MarketDataUpdateHandler;
import com.lunar.strategy.scoreboard.MarketOrderHandler.MarketOrder;
import com.lunar.strategy.scoreboard.stats.WarrantTradesStats;

import static org.apache.logging.log4j.util.Unbox.box;

import java.util.Iterator;

public class WarrantTradesAnalyser implements MarketDataUpdateHandler {
    static final Logger LOG = LogManager.getLogger(WarrantTradesAnalyser.class);
    
    final private ScoreBoardSecurityInfo security;
    final private WarrantTradesStats stats;
    final private SpreadTable spreadTable;
    
    private int nextOrderId = 1;
    private int punterTriggerId = 0;
    private int mmBidPrice = 0;
    private int mmBidLevel = 0;
    private int mmSpread = Integer.MAX_VALUE;

    private long validBestBid;
    private long validBestAsk;
    
    private int bidLevel;
    private int askLevel;
    private long bidQuantity;
    private long askQuantity;
    private long originalBidQuantity;
    private long originalAskQuantity;    
    
    private final MarketOrder orderInstance;
    private MarketOrder prevMarketOrder;
    
    private long lastPunterBuyTime;
    
    public WarrantTradesAnalyser(final ScoreBoardSecurityInfo security) {
        this.security = security;
        this.stats = security.scoreBoard().punterTradeSetStats();
        this.spreadTable = SpreadTableBuilder.get();
        this.orderInstance = new MarketOrder();
        security.registerMdUpdateHandler(this);
    }

    private long mmBidSize() {
        return security.marketContext().mmBidSizeThreshold() == 0 ? security.wrtParams().mmBidSize() : security.marketContext().mmBidSizeThreshold(); 
    }
    
    private long mmAskSize() {
        return security.marketContext().mmAskSizeThreshold() == 0 ? security.wrtParams().mmAskSize() : security.marketContext().mmAskSizeThreshold(); 
    }
    
    @Override
    public void onOrderBookUpdated(final Security baseSecurity, final long transactTime, final MarketOrderBook orderBook) throws Exception {
        int bidLevel = 0;
        int askLevel = 0;
        long bidQuantity = 0;
        long askQuantity = 0;
        final long prevOriginalBidQuantity = this.originalBidQuantity;
        final long prevOriginalAskQuantity = this.originalAskQuantity;
        processTick();
        if (!this.security.orderBook().bidSide().isEmpty()) {
            final Tick tick = this.security.orderBook().bidSide().bestOrNullIfEmpty(); 
            validBestBid = tick.price();
            bidLevel = tick.tickLevel();
            bidQuantity = tick.qty();
        }
        if (!this.security.orderBook().askSide().isEmpty()) {
            final Tick tick = this.security.orderBook().askSide().bestOrNullIfEmpty();
            validBestAsk = tick.price();
            askLevel = tick.tickLevel();
            askQuantity = tick.qty();
        }
        if (bidLevel != this.bidLevel || (prevMarketOrder == null && bidQuantity != this.bidQuantity)) {
            this.originalBidQuantity = bidQuantity;
        }
        this.bidLevel = bidLevel;
        this.bidQuantity = bidQuantity;

        if (askLevel != this.askLevel || (prevMarketOrder == null && askQuantity != this.askQuantity)) {
            this.originalAskQuantity = askQuantity;
        }
        this.askLevel = askLevel;
        this.askQuantity = askQuantity;

        if (prevMarketOrder != null) {
            switch (prevMarketOrder.side) {
            case ASK:
                {
                    // if the lifted quantity is the same as the full quantity on market
                    if (prevMarketOrder.quantity == prevMarketOrder.quantityOnMarket) {
                        final Tick bidTick = security.orderBook().bidSide().bestOrNullIfEmpty();
                        final boolean liftedByIssuer = bidTick != null && bidTick.price() == prevMarketOrder.price && bidTick.qty() + prevMarketOrder.quantity >= mmBidSize();
                        if (liftedByIssuer) {
                            // issuer lifted ask side orders (i.e. punter or retailers selling back)
                            // TODO because we aggregate trades in mds, we cannot handle multiple sell-backs. if numTrades > 1, there are more than one sellers which we cannot handle
                            handleSellTrade(prevMarketOrder.price, prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                            if (prevMarketOrder.quantity < 1000000) {
                                if (prevMarketOrder.numTrades == 1) {
                                    handlePunterSellTrade(transactTime);
                                }
                                else {
                                    LOG.warn("Cannot handle multiple sell orders from punters/retailers due to aggregated trades: secCode {}, price {}, quantity {}, triggerSeqNum {}", security.code(), box(prevMarketOrder.price), box(prevMarketOrder.quantity), box(prevMarketOrder.triggerSeqNum));
                                }
                            }
                        }
                        else if (prevOriginalAskQuantity >= mmAskSize()) {
                            // punter/retailer lifted the entire price level from issuer
                            // doubtful for punters to do this. likely TO-maker or retailer
                            handleBuyTrade(prevMarketOrder.price, prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                            //if (prevMarketOrder.quantity < 1000000) {
                            //	handlePunterBuyTrade(transactTime);
                            //}
                        }
                        else {
                            handleUncertainTrade(prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                            // punter or issuer lifted from retailer/punter
                            //handlePunterSellTrade(transactTime);
                            //handlePunterBuyTrade(transactTime);
                        }
                    }
                    else {
                        if (prevOriginalAskQuantity >= mmAskSize()) {
                            handleBuyTrade(prevMarketOrder.price, prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                            if (prevMarketOrder.quantity < 1000000) {
                                handlePunterBuyTrade(transactTime);
                            }
                        }
                        else {
                            handleUncertainTrade(prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                        }
                    }
                    break;
                }
            case BID:
                {
                    // if the hit quantity is the same as the full quantity on market
                    if (prevMarketOrder.quantity == prevMarketOrder.quantityOnMarket) {
                        final Tick askTick = security.orderBook().askSide().bestOrNullIfEmpty();
                        final boolean hittedByIssuer = askTick != null && askTick.price() == prevMarketOrder.price && askTick.qty() + prevMarketOrder.quantity >= mmAskSize();
                        if (hittedByIssuer) {
                            handleBuyTrade(prevMarketOrder.price, prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                            if (prevMarketOrder.quantity < 1000000) {
                                // issuer hit bid side orders  of retailers (i.e. retailers buying)
                                // it is doubtful for punters to place limit orders to buy                                
                                LOG.warn("Detected buy order from retailer lifted by issuer: secCode {}, price {}, quantity {}, triggerSeqNum {}", security.code(), box(prevMarketOrder.price), box(prevMarketOrder.quantity), box(prevMarketOrder.triggerSeqNum));
                            }
                        }
                        else if (prevOriginalBidQuantity >= mmBidSize()) {
                            // punter/retailer hitting the entire price level from issuer
                            // doubtful for punters to do this. likely TO-maker or retailer
                            handleSellTrade(prevMarketOrder.price, prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                            if (prevMarketOrder.quantity < 1000000) {
                                handlePunterSellTrade(transactTime);
                            }
                        }
                        else {
                            // punter or issuer hit bid side order of retailer
                            handleUncertainTrade(prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                            if (prevMarketOrder.quantity < 1000000) {
                                handlePunterSellTrade(transactTime);
                            }
                        }
                    }
                    else {
                        if (prevOriginalBidQuantity >= mmBidSize()) {
                            handleSellTrade(prevMarketOrder.price, prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                        }
                        else {
                            handleUncertainTrade(prevMarketOrder.grossPrice, prevMarketOrder.quantity);
                        }
                        if (prevMarketOrder.quantity < 1000000) {
                            handlePunterSellTrade(transactTime);
                        }
                    }
                    break;
                }
            }
            prevMarketOrder = null;
        }
        final long previousNetPnl = stats.getNetPnl();
        final long previousGrossPnl = stats.getGrossPnl();
        if (stats.getNetSold() > 0) {            
            stats.setNetPnl(stats.getNetRevenue() - stats.getNetSold() * validBestBid);
        }
        else if (stats.getNetSold() < 0) {
            stats.setNetPnl(stats.getNetRevenue() - stats.getNetSold() * validBestAsk);
        }
        if (stats.getGrossSold() > 0) {
            stats.setGrossPnl(stats.getGrossRevenue() - stats.getGrossSold() * validBestBid);
        }
        
        if (previousNetPnl != stats.getNetPnl() || previousGrossPnl != stats.getGrossPnl()) {
            LOG.info("Issuer pnl analysis 1: secCode {}, numberTrades {}, numberUncertainTrades {}, numberBigTrades {}, numBuy {}, totalBuyPrice {}, numSell {}, totalSellPrice {}"
                    + ", netSold {}, netRevenue {}, netPnl {}, "
                    + ", grossSold {}, grossRevenue {}, grossPnl {}, trigger seqNum {}",
                    security.code(), box(stats.getNumberTrades()), box(stats.getNumberUncertainTrades()), box(stats.getNumberBigTrades()), box(stats.getNumBuy()), box(stats.getTotalBuyPrice()), box(stats.getNumSell()), box(stats.getTotalSellPrice()),
                    box(stats.getNetSold()), box(stats.getNetRevenue()), box(stats.getNetPnl()),
                    box(stats.getGrossSold()), box(stats.getGrossRevenue()), box(stats.getGrossSold()), box(security.orderBook().triggerInfo().triggerSeqNum()));
        }
    }

    @Override
    public void onTradeReceived(final Security baseSecurity, final long timestamp, final MarketTrade trade)
            throws Exception {
        if (prevMarketOrder == null) {
            prevMarketOrder = orderInstance;
            prevMarketOrder.triggerSeqNum = security.orderBook().triggerInfo().triggerSeqNum();
            prevMarketOrder.orderTime = timestamp;
            prevMarketOrder.side = trade.side();
            prevMarketOrder.price = trade.price();
            prevMarketOrder.mmBid = mmBidPrice;
            prevMarketOrder.tickLevel = spreadTable.priceToTick(trade.price());
            prevMarketOrder.quantity = trade.quantity();
            prevMarketOrder.grossPrice = trade.price() * trade.quantity();
            prevMarketOrder.spread_3L = mmSpread != Integer.MAX_VALUE || mmSpread != 0 ? mmSpread * 1000 : Long.MAX_VALUE;
            prevMarketOrder.quantityOnMarket = 0;
            prevMarketOrder.numTrades = trade.numActualTrades();
            prevMarketOrder.hasDoubt = false;
            prevMarketOrder.orderId = nextOrderId++;
            prevMarketOrder.punterTriggerId = 0;
            switch (prevMarketOrder.side) {
            case ASK:
                {
                    final Tick tick = security.orderBook().bestAskOrNullIfEmpty();
                    if (tick != null) {
                        prevMarketOrder.quantityOnMarket = tick.qty();
                    }
                }
                break;
            case BID:
                {
                    final Tick tick = security.orderBook().bestBidOrNullIfEmpty();
                    if (tick != null) {
                        prevMarketOrder.quantityOnMarket = tick.qty();
                    }
                }
                break;
            }
        }
        else {
            prevMarketOrder.quantity += trade.quantity();
            prevMarketOrder.grossPrice += trade.price() * trade.quantity();
            prevMarketOrder.numTrades += trade.numActualTrades();
        }
    }

    private void processTick() {
        mmBidPrice = 0;
        mmBidLevel = 0;
        if (!security.orderBook().bidSide().isEmpty()) {
            Tick tick = security.orderBook().bidSide().bestOrNullIfEmpty();
            if (tick.qty() >= mmBidSize()) {
                mmBidPrice = tick.price();
                mmBidLevel = tick.tickLevel();
            }
            else {
                final Iterator<Tick> iterator = security.orderBook().bidSide().localPriceLevelsIterator();
                while (iterator.hasNext()) {
                    tick = iterator.next();
                    if (tick.qty() >= mmBidSize()) {
                        mmBidPrice = tick.price();
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
    
    private boolean handlePunterBuyTrade(final long transactTime) {
        if (prevMarketOrder.orderTime - lastPunterBuyTime > ScoreBoardCalculator.MIN_TIME_BETWEEN_TRADE_TRIGGERS) {
            punterTriggerId++;
        }
        lastPunterBuyTime = prevMarketOrder.orderTime;
        prevMarketOrder.punterTriggerId = punterTriggerId;
        if (security.marketOrderHandler() != null) {
            security.marketOrderHandler().onBuyTrade(transactTime, prevMarketOrder);
        }
        return true;
    }
    
    private boolean handlePunterSellTrade(final long transactTime) {
        if (security.marketOrderHandler() != null) {
            security.marketOrderHandler().onSellTrade(transactTime, prevMarketOrder, null);
        }            
        return true;
    }
    
    private void handleSellTrade(final int price, final long grossPrice, final long quantity) {
        stats.setNumberTrades(stats.getNumberTrades() + 1);
        if (quantity > 1000000) {
            stats.setNumberBigTrades(stats.getNumberBigTrades() + 1);
        }
        else {
            stats.setNumBuy(stats.getNumBuy() + quantity);
            stats.setTotalBuyPrice(stats.getTotalBuyPrice() + grossPrice);
            stats.setNetRevenue(stats.getNetRevenue() - grossPrice);
            stats.setNetSold(stats.getNetSold() - quantity);
            if (quantity > stats.getGrossSold()) {
                stats.setGrossRevenue(stats.getGrossRevenue() - (grossPrice * stats.getGrossSold()) / quantity);
                stats.setGrossSold(0);
            }
            else {
                stats.setGrossSold(stats.getGrossSold() - quantity);
                stats.setGrossRevenue(stats.getGrossRevenue() - grossPrice);
            }
        }
    }
    
    private void handleBuyTrade(final int price, final long grossPrice, final long quantity) {
        stats.setNumberTrades(stats.getNumberTrades() + 1);
        if (quantity > 1000000) {
            stats.setNumberBigTrades(stats.getNumberBigTrades() + 1);
        }
        else {
            stats.setNumSell(stats.getNumSell() + quantity);
            stats.setTotalSellPrice(stats.getTotalSellPrice() + grossPrice);            
            stats.setNetRevenue(stats.getNetRevenue() + grossPrice);
            stats.setNetSold(stats.getNetSold() + quantity);
            stats.setGrossSold(stats.getGrossSold() + quantity);
            stats.setGrossRevenue(stats.getGrossRevenue() + grossPrice);
        }
    }
    
    private void handleUncertainTrade(final long grossPrice, final long quantity) {
        stats.setNumberTrades(stats.getNumberTrades() + 1);
        stats.setNumberUncertainTrades(stats.getNumberUncertainTrades() + 1);
    }

}
