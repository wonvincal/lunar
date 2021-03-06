// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class HeartbeatFbs extends Table {
  public static HeartbeatFbs getRootAsHeartbeatFbs(ByteBuffer _bb) { return getRootAsHeartbeatFbs(_bb, new HeartbeatFbs()); }
  public static HeartbeatFbs getRootAsHeartbeatFbs(ByteBuffer _bb, HeartbeatFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public HeartbeatFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public long nanoOfDay() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }

  public static int createHeartbeatFbs(FlatBufferBuilder builder,
      long nanoOfDay) {
    builder.startObject(1);
    HeartbeatFbs.addNanoOfDay(builder, nanoOfDay);
    return HeartbeatFbs.endHeartbeatFbs(builder);
  }

  public static void startHeartbeatFbs(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addNanoOfDay(FlatBufferBuilder builder, long nanoOfDay) { builder.addLong(0, nanoOfDay, 0L); }
  public static int endHeartbeatFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

