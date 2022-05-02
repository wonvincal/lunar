package com.lunar.order;

import com.lunar.message.io.sbe.OrderSbeDecoder;
import com.lunar.message.io.sbe.TradeSbeDecoder;

public interface OrderTradePersister {
    void persistOrder(final OrderSbeDecoder order) throws Exception;
    void persistTrade(final TradeSbeDecoder trade) throws Exception;
}
