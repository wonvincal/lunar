// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class VolAtPriceFbs extends Table {
  public static VolAtPriceFbs getRootAsVolAtPriceFbs(ByteBuffer _bb) { return getRootAsVolAtPriceFbs(_bb, new VolAtPriceFbs()); }
  public static VolAtPriceFbs getRootAsVolAtPriceFbs(ByteBuffer _bb, VolAtPriceFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public VolAtPriceFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public int price() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long bidQty() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public long askQty() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public boolean significant() { int o = __offset(10); return o != 0 ? 0!=bb.get(o + bb_pos) : false; }
  public byte volClusterType() { int o = __offset(12); return o != 0 ? bb.get(o + bb_pos) : 0; }

  public static int createVolAtPriceFbs(FlatBufferBuilder builder,
      int price,
      long bidQty,
      long askQty,
      boolean significant,
      byte volClusterType) {
    builder.startObject(5);
    VolAtPriceFbs.addAskQty(builder, askQty);
    VolAtPriceFbs.addBidQty(builder, bidQty);
    VolAtPriceFbs.addPrice(builder, price);
    VolAtPriceFbs.addVolClusterType(builder, volClusterType);
    VolAtPriceFbs.addSignificant(builder, significant);
    return VolAtPriceFbs.endVolAtPriceFbs(builder);
  }

  public static void startVolAtPriceFbs(FlatBufferBuilder builder) { builder.startObject(5); }
  public static void addPrice(FlatBufferBuilder builder, int price) { builder.addInt(0, price, 0); }
  public static void addBidQty(FlatBufferBuilder builder, long bidQty) { builder.addLong(1, bidQty, 0L); }
  public static void addAskQty(FlatBufferBuilder builder, long askQty) { builder.addLong(2, askQty, 0L); }
  public static void addSignificant(FlatBufferBuilder builder, boolean significant) { builder.addBoolean(3, significant, false); }
  public static void addVolClusterType(FlatBufferBuilder builder, byte volClusterType) { builder.addByte(4, volClusterType, 0); }
  public static int endVolAtPriceFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}
