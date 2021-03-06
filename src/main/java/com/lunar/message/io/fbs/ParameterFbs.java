// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class ParameterFbs extends Table {
  public static ParameterFbs getRootAsParameterFbs(ByteBuffer _bb) { return getRootAsParameterFbs(_bb, new ParameterFbs()); }
  public static ParameterFbs getRootAsParameterFbs(ByteBuffer _bb, ParameterFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public ParameterFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public byte parameterType() { int o = __offset(4); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public String parameterValue() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer parameterValueAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public long parameterValueLong() { int o = __offset(8); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }

  public static int createParameterFbs(FlatBufferBuilder builder,
      byte parameterType,
      int parameterValueOffset,
      long parameterValueLong) {
    builder.startObject(3);
    ParameterFbs.addParameterValueLong(builder, parameterValueLong);
    ParameterFbs.addParameterValue(builder, parameterValueOffset);
    ParameterFbs.addParameterType(builder, parameterType);
    return ParameterFbs.endParameterFbs(builder);
  }

  public static void startParameterFbs(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addParameterType(FlatBufferBuilder builder, byte parameterType) { builder.addByte(0, parameterType, 0); }
  public static void addParameterValue(FlatBufferBuilder builder, int parameterValueOffset) { builder.addOffset(1, parameterValueOffset, 0); }
  public static void addParameterValueLong(FlatBufferBuilder builder, long parameterValueLong) { builder.addLong(2, parameterValueLong, 0L); }
  public static int endParameterFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

