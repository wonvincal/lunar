package com.lunar.order;

/**
 * Line handler specific line handler
 * @author wongca
 *
 */
public interface LineHandlerEngineExceptionHandler {
    /**
     * Connection is disconnected.  Line handler needs to be restarted.
     * If the underlying implementation supports auto-reconnect itself, we should wait a while before
     * attempting to reconnect.
     */
    default public void onDisconnected(){};
    /**
     * An error is encountered.  Line handler needs to be restarted.
     */
    default public void onError(Throwable cause){};
    
	public static LineHandlerEngineExceptionHandler NULL_HANDLER = new LineHandlerEngineExceptionHandler(){
	    
	};
}
