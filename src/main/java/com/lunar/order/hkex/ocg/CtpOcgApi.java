package com.lunar.order.hkex.ocg;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.TimerService;
import com.lunar.message.Command;
import com.lunar.service.ServiceConstant;

public class CtpOcgApi {
    private static final Logger LOG = LogManager.getLogger(CtpOcgApi.class);
	// Buy Sell Code
	static public final int SELL = 1;
	static public final int BUY = 2;
	
	// Order Type Code
	static public final int MARKET = 1;
	static public final int LIMIT = 2;
	static public final int MARKET_TO_LIMIT = 4;
	static public final int LIMIT_TO_MARKET = 5;
	static public final int ON_OPEN = 16;
	static public final int ON_CLOSE = 32;
	static public final int STOP_MARKET = 64;
	static public final int STOP_LIMIT = 128;
	static public final int ICEBERG = 256;
	static public final int AUCTION_MARKET = 1024;
	static public final int AUCTION_LIMIT = 2048;
	static public final int AVERAGE = 4096;
	static public final int ONE_CANCELS_THE_OTHER = 8192;
	static public final int PEGGED_MARKET = 32768;
	static public final int PEGGED_PRIMARY = 65536;
	static public final int PEGGED_MID = 131072;
	
	// Time In Force Code
	static public final int FILL_OR_KILL = 1; 
	static public final int ALL_OR_NONE = 2;
	static public final int IMMEDIATE_OR_CANCEL = 4; 
	static public final int GOOD_IN_SESSION = 8;
	static public final int GOOD_TILL_CANCELLED = 16;
	static public final int GOOD_TILL_DATE = 32;
	static public final int GOOD_FOR_DAY = 64;
	static public final int AT_THE_OPENING = 128;
	static public final int GOOD_TILL_TIME = 256;
	static public final int AT_THE_CLOSE = 512;
	static public final int GOOD_FOR_INTRADAY_AUCTION = 1024;
	static public final int GOOD_FOR_AUCTION = 2048;
	
	// Order Status Code
    static public final char NEW = '0';
    static public final char PARTIALLY_FILLED = '1';
    static public final char FILLED = '2';
    static public final char DONE_FOR_DAY = '3';
    static public final char CANCELED = '4';
    static public final char REPLACED = '5';
    static public final char PENDING_CANCEL = '6';
    static public final char STOPPED = '7';
    static public final char REJECTED = '8';
    static public final char SUSPENDED = '9';
    static public final char PENDING_NEW = 'A';
    static public final char CALCULATED = 'B';
    static public final char EXPIRED = 'C';
    static public final char ACCEPTED_FOR_BIDDING = 'D';
    static public final char PENDING_REPLACE = 'E';
    static public final char UNKNOWN = 70;

    // Session State
    static public final int CONNECTED = 0;
    static public final int CONNECTING = 1;
    static public final int DISCONNECTING = 2;
    static public final int DISCONNECTED = 3;

	// Error Code
	static public final int ERROR_UNKNOWN = 0;
	static public final int ERROR_UNSUPPORTED = 1;
	static public final int ERROR_NOT_READY = 2;
	static public final int ERROR_NOT_CONNECTED = 4;
	static public final int ERROR_NOT_LOGGED_IN = 8;
	static public final int ERROR_ALREADY_CONNECTED = 16;
	static public final int ERROR_ALREADY_LOGGED_IN = 32;
	static public final int ERROR_ALREADY_SUBSCRIBED = 64;
	static public final int ERROR_INVALID_ACCOUNT = 128;
	static public final int ERROR_INVALID_FILTER = 256;
	static public final int ERROR_INVALID_DATA = 512;
	static public final int ERROR_REJECTED = 1024;
	static public final int ERROR_BUSY = 2048;

	static {
		if (ServiceConstant.LOAD_JNI){
			System.loadLibrary("ctpocgapi");
		}
	}
	
	public interface CallbackHandler {
		int onExecutionReport(int threadId, int clientOrderId, String exchangeOrderId, long dateTime, int execTypeCode, int execTransTypeCode, char orderStatusCode, long secSid, int buySellCode, int orderPrice, int orderQuantity, int orderTotalTradeQuantity, int leavesQuantity, String executionId, int tradeQuantity, int tradePrice, String errorText, boolean inSyncFlag);
		int onSessionState(int threadId, int sessionState);
		int onEndRecovery(int threadId);
	}

	private boolean isWarmup;
    private MoreOperationHandler moreOperationHandler;
    private MoreOperationHandler moreOperationHandlerForWarmup;
	private CallbackHandler handler;
	
	public CtpOcgApi(CallbackHandler handler, TimerService timerService) {
	    isWarmup = false;
	    moreOperationHandler = MoreOperationHandler.NULL_INSTANCE;
	    this.handler = handler;
	    this.moreOperationHandlerForWarmup = CtpOcgApiWarmupOperationHandler.of("ctpocgapi-warmup", timerService, handler);
	};
	
	private native int initialize(String connectorFile, String configFile, String user, String account, int date, int startClientOrderId);
	private native int setMode(boolean warmup);
	private native int close();
	private native int endRecovery(long nanoOfDay);
	private native int addOrder(long nanoOfDay, int clientOrderId, long secSid, int side, int orderType, int timeInForce, int price, int quantity, boolean isEnhanced);
	private native int cancelOrder(long nanoOfDay, String clientOrderId, long secSid, int side);	
	private native int massCancelAll(long nanoOfDay);
	private native int massCancelBySecurity(long nanoOfDay, long secSid);
	private native int massCancelBySide(long nanoOfDay, int side);
	private native void displayOrderInfo(int clientOrderId);
	private native void displayAllOrderInfo();

	public int invokeInitialize(String connectorFile, String configFile, String user, String account, int date, int startClientOrderId){
	    return initialize(connectorFile, configFile, user, account, date, startClientOrderId);
	}
	
    public int invokeSetMode(boolean warmup){
	    if (warmup){
	        moreOperationHandler = moreOperationHandlerForWarmup;
	    }
	    else {
	        moreOperationHandler = MoreOperationHandler.NULL_INSTANCE;
	    }
	    return setMode(warmup);
	}
	
    public int invokeClose(){
	    return close();
	}
	
    public int connect(long nanoOfDay){
        // CTP software requires as to pass in a nanoOfDay
        // endRecovery is used to connect to HKEx
	    return endRecovery(nanoOfDay);
	}
	
    public int invokeAddOrder(long nanoOfDay, int clientOrderId, long secSid, int side, int orderType, int timeInForce, int price, int quantity, boolean isEnhanced){
	    int result = addOrder(nanoOfDay, clientOrderId, secSid, side, orderType, timeInForce, price, quantity, isEnhanced);
	    moreOperationHandler.afterAddOrderSent(result, nanoOfDay, clientOrderId, secSid, side, orderType, timeInForce, price, quantity, isEnhanced);
	    return result;
	}
	
    public int invokeCancelOrder(long nanoOfDay, String clientOrderId, long secSid, int side){
	    int result = cancelOrder(nanoOfDay, clientOrderId, secSid, side);
	    moreOperationHandler.afterCancelOrderSent(result, nanoOfDay, clientOrderId, secSid, side);
	    return result;
	}
	
    public int invokeMassCancelAll(long nanoOfDay){
	    int result = massCancelAll(nanoOfDay);
	    moreOperationHandler.afterMassCancelAllSent(result, nanoOfDay);
	    return result;
	}
	
    public int invokeMassCancelBySecurity(long nanoOfDay, long secSid){
	    int result = massCancelBySecurity(nanoOfDay, secSid);
	    moreOperationHandler.afterMassCancelBySecuritySent(result, nanoOfDay, secSid);
	    return result;
	}
	
    public int invokeMassCancelBySide(long nanoOfDay, int side){
	    int result = massCancelBySide(nanoOfDay, side);
	    moreOperationHandler.afterMassCancelBySideSent(result, nanoOfDay, side);
	    return result;
	}
	
	public void invokeDisplayOrderInfo(int clientOrderId){
		displayOrderInfo(clientOrderId);
	}
	
	public void invokeDisplayAllOrderInfo(){
		displayAllOrderInfo();
	}
	
	public int onExecutionReport(int threadId, int clientOrderId, String exchangeOrderId, long dateTime, int execTypeCode, int execTransTypeCode, char orderStatusCode, long secSid, int buySellCode, int orderPrice, int orderQuantity, int orderTotalTradeQuantity, int leavesQuantity, String executionId, int tradeQuantity, int tradePrice, String errorText, boolean inSyncFlag) {
		return handler.onExecutionReport(threadId, clientOrderId, exchangeOrderId, dateTime, execTypeCode, execTransTypeCode, orderStatusCode, secSid, buySellCode, orderPrice, orderQuantity, orderTotalTradeQuantity, leavesQuantity, executionId, tradeQuantity, tradePrice, errorText, inSyncFlag);
	}
	
	public int onSessionState(int threadId, int sessionState) {
		return handler.onSessionState(threadId, sessionState);
	}
	
	public int onEndRecovery(int threadId) {
	    return handler.onEndRecovery(threadId);
	}

    public int apply(Command command) {
        return moreOperationHandler.apply(command);
    }
    
    public static interface MoreOperationHandler {
        void afterAddOrderSent(int result, long nanoOfDay, int clientOrderId, long secSid, int side, int orderType, int timeInForce, int price, int quantity, boolean isEnhanced);
        void afterCancelOrderSent(int result, long nanoOfDay, String clientOrderId, long secSid, int side);
        void afterMassCancelAllSent(int result, long nanoOfDay);
        void afterMassCancelBySecuritySent(int result, long nanoOfDay, long secSid);
        void afterMassCancelBySideSent(int result, long nanoOfDay, int side);
        int apply(Command command);
        
        public static MoreOperationHandler NULL_INSTANCE = new MoreOperationHandler() {
            
            @Override
            public void afterMassCancelBySideSent(int result, long nanoOfDay, int side) {
            }
            
            @Override
            public void afterMassCancelBySecuritySent(int result, long nanoOfDay, long secSid) {
            }
            
            @Override
            public void afterMassCancelAllSent(int result, long nanoOfDay) {
            }
            
            @Override
            public void afterCancelOrderSent(int result, long nanoOfDay, String clientOrderId, long secSid, int side) {
            }
            
            @Override
            public void afterAddOrderSent(int result, long nanoOfDay, int clientOrderId, long secSid, int side,
                    int orderType, int timeInForce, int price, int quantity, boolean isEnhanced) {
            }
            
            @Override
            public int apply(Command command) {
                return 0;
            }
        }; 
    }
}

