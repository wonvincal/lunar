// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class StrategySwitchFbs extends Table {
  public static StrategySwitchFbs getRootAsStrategySwitchFbs(ByteBuffer _bb) { return getRootAsStrategySwitchFbs(_bb, new StrategySwitchFbs()); }
  public static StrategySwitchFbs getRootAsStrategySwitchFbs(ByteBuffer _bb, StrategySwitchFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public StrategySwitchFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public int senderSinkId() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public byte switchType() { int o = __offset(6); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte switchSource() { int o = __offset(8); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public long sourceSid() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public boolean onOff() { int o = __offset(12); return o != 0 ? 0!=bb.get(o + bb_pos) : false; }

  public static int createStrategySwitchFbs(FlatBufferBuilder builder,
      int senderSinkId,
      byte switchType,
      byte switchSource,
      long sourceSid,
      boolean onOff) {
    builder.startObject(5);
    StrategySwitchFbs.addSourceSid(builder, sourceSid);
    StrategySwitchFbs.addSenderSinkId(builder, senderSinkId);
    StrategySwitchFbs.addOnOff(builder, onOff);
    StrategySwitchFbs.addSwitchSource(builder, switchSource);
    StrategySwitchFbs.addSwitchType(builder, switchType);
    return StrategySwitchFbs.endStrategySwitchFbs(builder);
  }

  public static void startStrategySwitchFbs(FlatBufferBuilder builder) { builder.startObject(5); }
  public static void addSenderSinkId(FlatBufferBuilder builder, int senderSinkId) { builder.addInt(0, senderSinkId, 0); }
  public static void addSwitchType(FlatBufferBuilder builder, byte switchType) { builder.addByte(1, switchType, 0); }
  public static void addSwitchSource(FlatBufferBuilder builder, byte switchSource) { builder.addByte(2, switchSource, 0); }
  public static void addSourceSid(FlatBufferBuilder builder, long sourceSid) { builder.addLong(3, sourceSid, 0L); }
  public static void addOnOff(FlatBufferBuilder builder, boolean onOff) { builder.addBoolean(4, onOff, false); }
  public static int endStrategySwitchFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

