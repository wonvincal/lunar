package com.lunar.order;

import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;

public class NullOrderTradePersister implements OrderTradePersister {

    @Override
    public void persistOrder(OrderSbeDecoder order) throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void persistTrade(TradeSbeDecoder trade) throws Exception {
        // TODO Auto-generated method stub
        
    }

}
