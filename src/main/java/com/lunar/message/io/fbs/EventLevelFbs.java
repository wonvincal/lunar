// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

public final class EventLevelFbs {
  private EventLevelFbs() { }
  public static final byte OK = 0;
  public static final byte INFO = 1;
  public static final byte WARNING = 2;
  public static final byte CRITICAL = 3;

  public static final String[] names = { "OK", "INFO", "WARNING", "CRITICAL", };

  public static String name(int e) { return names[e]; }
}
