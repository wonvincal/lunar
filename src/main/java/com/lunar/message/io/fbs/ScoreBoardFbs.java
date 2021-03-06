// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class ScoreBoardFbs extends Table {
  public static ScoreBoardFbs getRootAsScoreBoardFbs(ByteBuffer _bb) { return getRootAsScoreBoardFbs(_bb, new ScoreBoardFbs()); }
  public static ScoreBoardFbs getRootAsScoreBoardFbs(ByteBuffer _bb, ScoreBoardFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public ScoreBoardFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public long secSid() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public int score() { int o = __offset(6); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public ScoreBoardDetailFbs details(int j) { return details(new ScoreBoardDetailFbs(), j); }
  public ScoreBoardDetailFbs details(ScoreBoardDetailFbs obj, int j) { int o = __offset(8); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int detailsLength() { int o = __offset(8); return o != 0 ? __vector_len(o) : 0; }

  public static int createScoreBoardFbs(FlatBufferBuilder builder,
      long secSid,
      int score,
      int detailsOffset) {
    builder.startObject(3);
    ScoreBoardFbs.addSecSid(builder, secSid);
    ScoreBoardFbs.addDetails(builder, detailsOffset);
    ScoreBoardFbs.addScore(builder, score);
    return ScoreBoardFbs.endScoreBoardFbs(builder);
  }

  public static void startScoreBoardFbs(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addSecSid(FlatBufferBuilder builder, long secSid) { builder.addLong(0, secSid, 0L); }
  public static void addScore(FlatBufferBuilder builder, int score) { builder.addInt(1, score, 0); }
  public static void addDetails(FlatBufferBuilder builder, int detailsOffset) { builder.addOffset(2, detailsOffset, 0); }
  public static int createDetailsVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startDetailsVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endScoreBoardFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

