// automatically generated, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class MarketDataOrderBookEntryFbs extends Table {
  public static MarketDataOrderBookEntryFbs getRootAsMarketDataOrderBookEntryFbs(ByteBuffer _bb) { return getRootAsMarketDataOrderBookEntryFbs(_bb, new MarketDataOrderBookEntryFbs()); }
  public static MarketDataOrderBookEntryFbs getRootAsMarketDataOrderBookEntryFbs(ByteBuffer _bb, MarketDataOrderBookEntryFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public MarketDataOrderBookEntryFbs __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long transactTime() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public int price() { int o = __offset(6); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public int tickLevel() { int o = __offset(8); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long quantity() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public int numOrders() { int o = __offset(12); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public byte entryType() { int o = __offset(14); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte priceLevel() { int o = __offset(16); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte side() { int o = __offset(18); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte updateAction() { int o = __offset(20); return o != 0 ? bb.get(o + bb_pos) : 0; }

  public static int createMarketDataOrderBookEntryFbs(FlatBufferBuilder builder,
      long transactTime,
      int price,
      int tickLevel,
      long quantity,
      int numOrders,
      byte entryType,
      byte priceLevel,
      byte side,
      byte updateAction) {
    builder.startObject(9);
    MarketDataOrderBookEntryFbs.addQuantity(builder, quantity);
    MarketDataOrderBookEntryFbs.addTransactTime(builder, transactTime);
    MarketDataOrderBookEntryFbs.addNumOrders(builder, numOrders);
    MarketDataOrderBookEntryFbs.addTickLevel(builder, tickLevel);
    MarketDataOrderBookEntryFbs.addPrice(builder, price);
    MarketDataOrderBookEntryFbs.addUpdateAction(builder, updateAction);
    MarketDataOrderBookEntryFbs.addSide(builder, side);
    MarketDataOrderBookEntryFbs.addPriceLevel(builder, priceLevel);
    MarketDataOrderBookEntryFbs.addEntryType(builder, entryType);
    return MarketDataOrderBookEntryFbs.endMarketDataOrderBookEntryFbs(builder);
  }

  public static void startMarketDataOrderBookEntryFbs(FlatBufferBuilder builder) { builder.startObject(9); }
  public static void addTransactTime(FlatBufferBuilder builder, long transactTime) { builder.addLong(0, transactTime, 0); }
  public static void addPrice(FlatBufferBuilder builder, int price) { builder.addInt(1, price, 0); }
  public static void addTickLevel(FlatBufferBuilder builder, int tickLevel) { builder.addInt(2, tickLevel, 0); }
  public static void addQuantity(FlatBufferBuilder builder, long quantity) { builder.addLong(3, quantity, 0); }
  public static void addNumOrders(FlatBufferBuilder builder, int numOrders) { builder.addInt(4, numOrders, 0); }
  public static void addEntryType(FlatBufferBuilder builder, byte entryType) { builder.addByte(5, entryType, 0); }
  public static void addPriceLevel(FlatBufferBuilder builder, byte priceLevel) { builder.addByte(6, priceLevel, 0); }
  public static void addSide(FlatBufferBuilder builder, byte side) { builder.addByte(7, side, 0); }
  public static void addUpdateAction(FlatBufferBuilder builder, byte updateAction) { builder.addByte(8, updateAction, 0); }
  public static int endMarketDataOrderBookEntryFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
};

