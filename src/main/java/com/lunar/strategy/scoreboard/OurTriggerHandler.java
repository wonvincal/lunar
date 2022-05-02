package com.lunar.strategy.scoreboard;

import com.lunar.message.io.sbe.PricingMode;

public interface OurTriggerHandler {
    public class OurTriggerExplain {
        public long triggerValue;
        public long undBid;
        public long undAsk;
        public long undSpot;
        public int delta;
        public PricingMode pricingMode;
        
        public void copyFrom(final OurTriggerExplain ourTriggerExplain) {
            this.triggerValue = ourTriggerExplain.triggerValue;
            this.undBid = ourTriggerExplain.undBid;
            this.undAsk = ourTriggerExplain.undAsk;
            this.undSpot = ourTriggerExplain.undSpot;
            this.delta = ourTriggerExplain.delta;
            this.pricingMode = ourTriggerExplain.pricingMode;
        }
    }
    
    void onOurTriggerBuyReceived(final long timestamp, final int price, final OurTriggerExplain ourTriggerExplain, final boolean isAdditionalTrigger, final long triggerSeqNum);
    void onOurTriggerSellReceived(final long timestamp, final int price, final OurTriggerExplain ourTriggerExplain, final long triggerSeqNum);
}
