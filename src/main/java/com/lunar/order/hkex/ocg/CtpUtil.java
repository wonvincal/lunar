package com.lunar.order.hkex.ocg;

import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.OrderType;
import com.lunar.message.io.sbe.Side;
import com.lunar.message.io.sbe.TimeInForce;

public class CtpUtil {

    /**
     * TODO Check to see if there is a faster way
     * @param buySellCode
     * @return
     */
    public static Side convertSide(int buySellCode){
        switch (buySellCode) {
        case CtpOcgApi.BUY:
            return Side.BUY;
        case CtpOcgApi.SELL:
            return Side.SELL;
        default:
            throw new IllegalArgumentException("Invalid buySellCode: " + buySellCode);
        }
    }
    
    public static int convertSide(Side side){
        switch (side){
        case BUY:
            return CtpOcgApi.BUY;
        case SELL:
            return CtpOcgApi.SELL;
        default:
            throw new IllegalArgumentException("Invalid side: " + side.name());
        }
    }
    
    public static TimeInForce convertTif(int timeInForce){
        switch (timeInForce){
        case CtpOcgApi.GOOD_FOR_DAY:
            return TimeInForce.DAY;
        case CtpOcgApi.IMMEDIATE_OR_CANCEL:
            return TimeInForce.FILL_AND_KILL;
        case CtpOcgApi.GOOD_TILL_CANCELLED:
            return TimeInForce.GOOD_TILL_CANCEL;
        case CtpOcgApi.GOOD_TILL_DATE:
            return TimeInForce.GOOD_TILL_DATE;
        default:
            throw new IllegalArgumentException("Invalid time in force: " + timeInForce);            
        }
    }
    
    public static OrderType convertOrderType(int orderType, boolean isEnhanced){
        switch (orderType){
        case CtpOcgApi.STOP_MARKET:
            return OrderType.STOP_ORDER;
        case CtpOcgApi.LIMIT:
            if (isEnhanced){
                return OrderType.ENHANCED_LIMIT_ORDER;
            }
            else {
                return OrderType.LIMIT_ORDER;
            }
        case CtpOcgApi.LIMIT_TO_MARKET:
            return OrderType.MARKET_LIMIT_ORDER;
        case CtpOcgApi.MARKET:
            return OrderType.MARKET_ORDER;
        case CtpOcgApi.STOP_LIMIT:
            return OrderType.STOP_LIMIT_ORDER;
        default:
            throw new IllegalArgumentException("Invalid order type: " + orderType);
        }
    }
    
    public static OrderStatus convertOrderStatus(int status){
        switch (status) {
        case CtpOcgApi.NEW:
            return OrderStatus.NEW;
        case CtpOcgApi.PARTIALLY_FILLED:
            return OrderStatus.PARTIALLY_FILLED;
        case CtpOcgApi.FILLED:
            return OrderStatus.FILLED;
        case CtpOcgApi.DONE_FOR_DAY:
            return OrderStatus.EXPIRED;
        case CtpOcgApi.CANCELED:
            return OrderStatus.CANCELLED;
        case CtpOcgApi.STOPPED:
            return OrderStatus.REJECTED;
        case CtpOcgApi.REJECTED:
            return OrderStatus.REJECTED;
        default:
            throw new IllegalArgumentException("Invalid order status: " + status);
        }
    }
    
    public static char convertOrderStatus(OrderStatus status){
        switch (status) {
        case NEW:
            return CtpOcgApi.NEW; 
        case PARTIALLY_FILLED:
            return CtpOcgApi.PARTIALLY_FILLED;
        case FILLED:
            return CtpOcgApi.FILLED;
        case EXPIRED:
            return CtpOcgApi.DONE_FOR_DAY;
        case CANCELLED:
            return CtpOcgApi.CANCELED;
        case REJECTED:
            return CtpOcgApi.REJECTED;
        default:
            throw new IllegalArgumentException("Invalid order status: " + status);
        }
    }
}
