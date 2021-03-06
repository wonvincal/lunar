// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class OrderFbs extends Table {
  public static OrderFbs getRootAsOrderFbs(ByteBuffer _bb) { return getRootAsOrderFbs(_bb, new OrderFbs()); }
  public static OrderFbs getRootAsOrderFbs(ByteBuffer _bb, OrderFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public OrderFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public int channelId() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long channelSnapshotSeq() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public int orderSid() { int o = __offset(8); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public int orderId() { int o = __offset(10); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long secSid() { int o = __offset(12); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public String extId() { int o = __offset(14); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer extIdAsByteBuffer() { return __vector_as_bytebuffer(14, 1); }
  public byte orderType() { int o = __offset(16); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public int quantity() { int o = __offset(18); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public byte side() { int o = __offset(20); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte tif() { int o = __offset(22); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public long createTime() { int o = __offset(24); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public long updateTime() { int o = __offset(26); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public boolean isAlgoOrder() { int o = __offset(28); return o != 0 ? 0!=bb.get(o + bb_pos) : false; }
  public int limitPrice() { int o = __offset(30); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public int stopPrice() { int o = __offset(32); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public byte status() { int o = __offset(34); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public int cumulativeQty() { int o = __offset(36); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public int leavesQty() { int o = __offset(38); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public byte orderRejectType() { int o = __offset(40); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public String reason() { int o = __offset(42); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer reasonAsByteBuffer() { return __vector_as_bytebuffer(42, 1); }
  public int parentOrderSid() { int o = __offset(44); return o != 0 ? bb.getInt(o + bb_pos) : 0; }

  public static int createOrderFbs(FlatBufferBuilder builder,
      int channelId,
      long channelSnapshotSeq,
      int orderSid,
      int orderId,
      long secSid,
      int extIdOffset,
      byte orderType,
      int quantity,
      byte side,
      byte tif,
      long createTime,
      long updateTime,
      boolean isAlgoOrder,
      int limitPrice,
      int stopPrice,
      byte status,
      int cumulativeQty,
      int leavesQty,
      byte orderRejectType,
      int reasonOffset,
      int parentOrderSid) {
    builder.startObject(21);
    OrderFbs.addUpdateTime(builder, updateTime);
    OrderFbs.addCreateTime(builder, createTime);
    OrderFbs.addSecSid(builder, secSid);
    OrderFbs.addChannelSnapshotSeq(builder, channelSnapshotSeq);
    OrderFbs.addParentOrderSid(builder, parentOrderSid);
    OrderFbs.addReason(builder, reasonOffset);
    OrderFbs.addLeavesQty(builder, leavesQty);
    OrderFbs.addCumulativeQty(builder, cumulativeQty);
    OrderFbs.addStopPrice(builder, stopPrice);
    OrderFbs.addLimitPrice(builder, limitPrice);
    OrderFbs.addQuantity(builder, quantity);
    OrderFbs.addExtId(builder, extIdOffset);
    OrderFbs.addOrderId(builder, orderId);
    OrderFbs.addOrderSid(builder, orderSid);
    OrderFbs.addChannelId(builder, channelId);
    OrderFbs.addOrderRejectType(builder, orderRejectType);
    OrderFbs.addStatus(builder, status);
    OrderFbs.addIsAlgoOrder(builder, isAlgoOrder);
    OrderFbs.addTif(builder, tif);
    OrderFbs.addSide(builder, side);
    OrderFbs.addOrderType(builder, orderType);
    return OrderFbs.endOrderFbs(builder);
  }

  public static void startOrderFbs(FlatBufferBuilder builder) { builder.startObject(21); }
  public static void addChannelId(FlatBufferBuilder builder, int channelId) { builder.addInt(0, channelId, 0); }
  public static void addChannelSnapshotSeq(FlatBufferBuilder builder, long channelSnapshotSeq) { builder.addLong(1, channelSnapshotSeq, 0L); }
  public static void addOrderSid(FlatBufferBuilder builder, int orderSid) { builder.addInt(2, orderSid, 0); }
  public static void addOrderId(FlatBufferBuilder builder, int orderId) { builder.addInt(3, orderId, 0); }
  public static void addSecSid(FlatBufferBuilder builder, long secSid) { builder.addLong(4, secSid, 0L); }
  public static void addExtId(FlatBufferBuilder builder, int extIdOffset) { builder.addOffset(5, extIdOffset, 0); }
  public static void addOrderType(FlatBufferBuilder builder, byte orderType) { builder.addByte(6, orderType, 0); }
  public static void addQuantity(FlatBufferBuilder builder, int quantity) { builder.addInt(7, quantity, 0); }
  public static void addSide(FlatBufferBuilder builder, byte side) { builder.addByte(8, side, 0); }
  public static void addTif(FlatBufferBuilder builder, byte tif) { builder.addByte(9, tif, 0); }
  public static void addCreateTime(FlatBufferBuilder builder, long createTime) { builder.addLong(10, createTime, 0L); }
  public static void addUpdateTime(FlatBufferBuilder builder, long updateTime) { builder.addLong(11, updateTime, 0L); }
  public static void addIsAlgoOrder(FlatBufferBuilder builder, boolean isAlgoOrder) { builder.addBoolean(12, isAlgoOrder, false); }
  public static void addLimitPrice(FlatBufferBuilder builder, int limitPrice) { builder.addInt(13, limitPrice, 0); }
  public static void addStopPrice(FlatBufferBuilder builder, int stopPrice) { builder.addInt(14, stopPrice, 0); }
  public static void addStatus(FlatBufferBuilder builder, byte status) { builder.addByte(15, status, 0); }
  public static void addCumulativeQty(FlatBufferBuilder builder, int cumulativeQty) { builder.addInt(16, cumulativeQty, 0); }
  public static void addLeavesQty(FlatBufferBuilder builder, int leavesQty) { builder.addInt(17, leavesQty, 0); }
  public static void addOrderRejectType(FlatBufferBuilder builder, byte orderRejectType) { builder.addByte(18, orderRejectType, 0); }
  public static void addReason(FlatBufferBuilder builder, int reasonOffset) { builder.addOffset(19, reasonOffset, 0); }
  public static void addParentOrderSid(FlatBufferBuilder builder, int parentOrderSid) { builder.addInt(20, parentOrderSid, 0); }
  public static int endOrderFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

