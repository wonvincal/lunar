package com.lunar.marketdata.hkex;

import java.nio.ByteBuffer;

public class CtpMduApi {
	// Trading Phase
    static public final int NA = 0;
    static public final int CLOSE = 1;
    static public final int CLOSING = 2;
    static public final int HALT = 4;
    static public final int FAST_MARKET = 8;
    static public final int PRE_CLOSING = 16;
    static public final int PRE_OPENING = 32;
    static public final int OPENING = 64;
    static public final int OPEN = 128;
    static public final int MAX = 129;

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
		System.loadLibrary("ctpmduapi");
	}
	
	public interface CtpApiCallbackHandler {
		int onMarketData(long secSid, int bufferSize);
		int onTrade(long secSid, int bufferSize);
		int onMarketStats(long secSid, int bufferSize);
		int onTradingPhase(int tradingPhase);
		int onSessionState(int sessionState);
	}
	
	private final CtpApiCallbackHandler handler;
	
	public CtpMduApi(final CtpApiCallbackHandler handler) {
		this.handler = handler;
	}

	public native int initialize(String connectorFile, String omdcConfigFile, String omddConfigFile, int senderSinkId, int bookDepth, ByteBuffer directBufferOrder, ByteBuffer directBufferTrade, ByteBuffer directBufferStats, int bufferSize);
	public native int registerSecurity(long secSid, String securityCode);
	public native int registerHsiFutures(long secSid, String securityCode);
	public native int registerHsceiFutures(long secSid, String securityCode);
	public native int close();
    public native int requestSnapshot(long secSid);
    public native long getNanoOfDay();

	public int onMarketData(long secSid, int bufferSize) {
		return handler.onMarketData(secSid, bufferSize);
	}
	
	public int onTrade(long secSid, int bufferSize) {
		return handler.onTrade(secSid, bufferSize);
	}	
	
	public int onTradingPhase(int tradingPhase) {
		return handler.onTradingPhase(tradingPhase);
	}
	
	public int onMarketStats(long secSid, int bufferSize) {
	    return handler.onMarketStats(secSid, bufferSize);
	}
	
	public int onSessionState(int sessionState) {
		return handler.onSessionState(sessionState);
	}
}

