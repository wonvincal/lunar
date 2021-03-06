// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class NoteFbs extends Table {
  public static NoteFbs getRootAsNoteFbs(ByteBuffer _bb) { return getRootAsNoteFbs(_bb, new NoteFbs()); }
  public static NoteFbs getRootAsNoteFbs(ByteBuffer _bb, NoteFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public NoteFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public int noteSid() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long entitySid() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public int createDate() { int o = __offset(8); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long createTime() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public int updateDate() { int o = __offset(12); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long updateTime() { int o = __offset(14); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public boolean isDeleted() { int o = __offset(16); return o != 0 ? 0!=bb.get(o + bb_pos) : false; }
  public boolean isArchived() { int o = __offset(18); return o != 0 ? 0!=bb.get(o + bb_pos) : false; }
  public String description() { int o = __offset(20); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer descriptionAsByteBuffer() { return __vector_as_bytebuffer(20, 1); }

  public static int createNoteFbs(FlatBufferBuilder builder,
      int noteSid,
      long entitySid,
      int createDate,
      long createTime,
      int updateDate,
      long updateTime,
      boolean isDeleted,
      boolean isArchived,
      int descriptionOffset) {
    builder.startObject(9);
    NoteFbs.addUpdateTime(builder, updateTime);
    NoteFbs.addCreateTime(builder, createTime);
    NoteFbs.addEntitySid(builder, entitySid);
    NoteFbs.addDescription(builder, descriptionOffset);
    NoteFbs.addUpdateDate(builder, updateDate);
    NoteFbs.addCreateDate(builder, createDate);
    NoteFbs.addNoteSid(builder, noteSid);
    NoteFbs.addIsArchived(builder, isArchived);
    NoteFbs.addIsDeleted(builder, isDeleted);
    return NoteFbs.endNoteFbs(builder);
  }

  public static void startNoteFbs(FlatBufferBuilder builder) { builder.startObject(9); }
  public static void addNoteSid(FlatBufferBuilder builder, int noteSid) { builder.addInt(0, noteSid, 0); }
  public static void addEntitySid(FlatBufferBuilder builder, long entitySid) { builder.addLong(1, entitySid, 0L); }
  public static void addCreateDate(FlatBufferBuilder builder, int createDate) { builder.addInt(2, createDate, 0); }
  public static void addCreateTime(FlatBufferBuilder builder, long createTime) { builder.addLong(3, createTime, 0L); }
  public static void addUpdateDate(FlatBufferBuilder builder, int updateDate) { builder.addInt(4, updateDate, 0); }
  public static void addUpdateTime(FlatBufferBuilder builder, long updateTime) { builder.addLong(5, updateTime, 0L); }
  public static void addIsDeleted(FlatBufferBuilder builder, boolean isDeleted) { builder.addBoolean(6, isDeleted, false); }
  public static void addIsArchived(FlatBufferBuilder builder, boolean isArchived) { builder.addBoolean(7, isArchived, false); }
  public static void addDescription(FlatBufferBuilder builder, int descriptionOffset) { builder.addOffset(8, descriptionOffset, 0); }
  public static int endNoteFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

