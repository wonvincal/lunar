// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class EventFbs extends Table {
  public static EventFbs getRootAsEventFbs(ByteBuffer _bb) { return getRootAsEventFbs(_bb, new EventFbs()); }
  public static EventFbs getRootAsEventFbs(ByteBuffer _bb, EventFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public EventFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public int sinkId() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long time() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public byte level() { int o = __offset(8); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte eventType() { int o = __offset(10); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte category() { int o = __offset(12); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public EventValueFbs values(int j) { return values(new EventValueFbs(), j); }
  public EventValueFbs values(EventValueFbs obj, int j) { int o = __offset(14); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int valuesLength() { int o = __offset(14); return o != 0 ? __vector_len(o) : 0; }
  public String description() { int o = __offset(16); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer descriptionAsByteBuffer() { return __vector_as_bytebuffer(16, 1); }

  public static int createEventFbs(FlatBufferBuilder builder,
      int sinkId,
      long time,
      byte level,
      byte eventType,
      byte category,
      int valuesOffset,
      int descriptionOffset) {
    builder.startObject(7);
    EventFbs.addTime(builder, time);
    EventFbs.addDescription(builder, descriptionOffset);
    EventFbs.addValues(builder, valuesOffset);
    EventFbs.addSinkId(builder, sinkId);
    EventFbs.addCategory(builder, category);
    EventFbs.addEventType(builder, eventType);
    EventFbs.addLevel(builder, level);
    return EventFbs.endEventFbs(builder);
  }

  public static void startEventFbs(FlatBufferBuilder builder) { builder.startObject(7); }
  public static void addSinkId(FlatBufferBuilder builder, int sinkId) { builder.addInt(0, sinkId, 0); }
  public static void addTime(FlatBufferBuilder builder, long time) { builder.addLong(1, time, 0L); }
  public static void addLevel(FlatBufferBuilder builder, byte level) { builder.addByte(2, level, 0); }
  public static void addEventType(FlatBufferBuilder builder, byte eventType) { builder.addByte(3, eventType, 0); }
  public static void addCategory(FlatBufferBuilder builder, byte category) { builder.addByte(4, category, 0); }
  public static void addValues(FlatBufferBuilder builder, int valuesOffset) { builder.addOffset(5, valuesOffset, 0); }
  public static int createValuesVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startValuesVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static void addDescription(FlatBufferBuilder builder, int descriptionOffset) { builder.addOffset(6, descriptionOffset, 0); }
  public static int endEventFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}
