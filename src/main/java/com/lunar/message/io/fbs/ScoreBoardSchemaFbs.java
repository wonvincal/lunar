// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class ScoreBoardSchemaFbs extends Table {
  public static ScoreBoardSchemaFbs getRootAsScoreBoardSchemaFbs(ByteBuffer _bb) { return getRootAsScoreBoardSchemaFbs(_bb, new ScoreBoardSchemaFbs()); }
  public static ScoreBoardSchemaFbs getRootAsScoreBoardSchemaFbs(ByteBuffer _bb, ScoreBoardSchemaFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public ScoreBoardSchemaFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public ScoreBoardSchemaFieldFbs fields(int j) { return fields(new ScoreBoardSchemaFieldFbs(), j); }
  public ScoreBoardSchemaFieldFbs fields(ScoreBoardSchemaFieldFbs obj, int j) { int o = __offset(4); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int fieldsLength() { int o = __offset(4); return o != 0 ? __vector_len(o) : 0; }

  public static int createScoreBoardSchemaFbs(FlatBufferBuilder builder,
      int fieldsOffset) {
    builder.startObject(1);
    ScoreBoardSchemaFbs.addFields(builder, fieldsOffset);
    return ScoreBoardSchemaFbs.endScoreBoardSchemaFbs(builder);
  }

  public static void startScoreBoardSchemaFbs(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addFields(FlatBufferBuilder builder, int fieldsOffset) { builder.addOffset(0, fieldsOffset, 0); }
  public static int createFieldsVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startFieldsVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endScoreBoardSchemaFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

