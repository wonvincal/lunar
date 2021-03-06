// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

public final class MessageTypeFbs {
  private MessageTypeFbs() { }
  public static final byte INVALID = 0;
  public static final byte MARKET_DATA_ORDER_BOOK_UPDATE = 1;
  public static final byte AGGREGATE_ORDER_BOOK_UPDATE = 2;
  public static final byte MARKET_DATE_TRADE = 3;
  public static final byte SECURITY = 4;
  public static final byte TIMER_EVENT = 5;
  public static final byte RISK_EVENT = 6;
  public static final byte COMMAND = 7;
  public static final byte COMMAND_RESULT = 8;
  public static final byte REQUEST = 9;
  public static final byte RESPONSE = 10;
  public static final byte REGISTER_RESPONSE = 11;
  public static final byte INVALID_MESSAGE_NOTIFICATION = 12;
  public static final byte ECHO = 13;
  public static final byte CONTEXT_UPDATE = 14;
  public static final byte EXCHANGE = 15;
  public static final byte SERVICE_STATUS = 16;
  public static final byte ORDER = 18;
  public static final byte STRATEGY_TYPE = 50;
  public static final byte STRAT_PARAM_UPDATE = 52;
  public static final byte STRAT_UND_PARAM_UPDATE = 53;
  public static final byte STRAT_WRT_PARAM_UPDATE = 54;
  public static final byte TRADE = 60;
  public static final byte STRATEGY_SWITCH = 61;
  public static final byte ISSUER = 62;
  public static final byte POSITION = 63;
  public static final byte RISK_CONTROL = 64;
  public static final byte BOOBS = 65;
  public static final byte MARKET_STATUS = 66;
  public static final byte GENERIC_TRACKER = 67;
  public static final byte STRAT_ISS_PARAM_UPDATE = 68;
  public static final byte EVENT = 69;
  public static final byte DIVIDEND_CURVE = 70;
  public static final byte GREEKS = 71;
  public static final byte ORDERBOOK_SNAPSHOT = 72;
  public static final byte MARKET_STATS = 75;
  public static final byte SCOREBOARD_SCHEMA = 77;
  public static final byte SCOREBOARD = 78;
  public static final byte NOTE = 79;
  public static final byte CHART_DATA = 80;
  public static final byte STRAT_ISS_UND_PARAM_UPDATE = 81;

  public static final String[] names = { "INVALID", "MARKET_DATA_ORDER_BOOK_UPDATE", "AGGREGATE_ORDER_BOOK_UPDATE", "MARKET_DATE_TRADE", "SECURITY", "TIMER_EVENT", "RISK_EVENT", "COMMAND", "COMMAND_RESULT", "REQUEST", "RESPONSE", "REGISTER_RESPONSE", "INVALID_MESSAGE_NOTIFICATION", "ECHO", "CONTEXT_UPDATE", "EXCHANGE", "SERVICE_STATUS", "", "ORDER", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "STRATEGY_TYPE", "", "STRAT_PARAM_UPDATE", "STRAT_UND_PARAM_UPDATE", "STRAT_WRT_PARAM_UPDATE", "", "", "", "", "", "TRADE", "STRATEGY_SWITCH", "ISSUER", "POSITION", "RISK_CONTROL", "BOOBS", "MARKET_STATUS", "GENERIC_TRACKER", "STRAT_ISS_PARAM_UPDATE", "EVENT", "DIVIDEND_CURVE", "GREEKS", "ORDERBOOK_SNAPSHOT", "", "", "MARKET_STATS", "", "SCOREBOARD_SCHEMA", "SCOREBOARD", "NOTE", "CHART_DATA", "STRAT_ISS_UND_PARAM_UPDATE", };

  public static String name(int e) { return names[e]; }
}

