// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class CommandFbs extends Table {
  public static CommandFbs getRootAsCommandFbs(ByteBuffer _bb) { return getRootAsCommandFbs(_bb, new CommandFbs()); }
  public static CommandFbs getRootAsCommandFbs(ByteBuffer _bb, CommandFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public CommandFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public byte toSend() { int o = __offset(4); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public int clientKey() { int o = __offset(6); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public byte commandType() { int o = __offset(8); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public long timeoutNs() { int o = __offset(10); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public ParameterFbs parameters(int j) { return parameters(new ParameterFbs(), j); }
  public ParameterFbs parameters(ParameterFbs obj, int j) { int o = __offset(12); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int parametersLength() { int o = __offset(12); return o != 0 ? __vector_len(o) : 0; }

  public static int createCommandFbs(FlatBufferBuilder builder,
      byte toSend,
      int clientKey,
      byte commandType,
      long timeoutNs,
      int parametersOffset) {
    builder.startObject(5);
    CommandFbs.addTimeoutNs(builder, timeoutNs);
    CommandFbs.addParameters(builder, parametersOffset);
    CommandFbs.addClientKey(builder, clientKey);
    CommandFbs.addCommandType(builder, commandType);
    CommandFbs.addToSend(builder, toSend);
    return CommandFbs.endCommandFbs(builder);
  }

  public static void startCommandFbs(FlatBufferBuilder builder) { builder.startObject(5); }
  public static void addToSend(FlatBufferBuilder builder, byte toSend) { builder.addByte(0, toSend, 0); }
  public static void addClientKey(FlatBufferBuilder builder, int clientKey) { builder.addInt(1, clientKey, 0); }
  public static void addCommandType(FlatBufferBuilder builder, byte commandType) { builder.addByte(2, commandType, 0); }
  public static void addTimeoutNs(FlatBufferBuilder builder, long timeoutNs) { builder.addLong(3, timeoutNs, 0L); }
  public static void addParameters(FlatBufferBuilder builder, int parametersOffset) { builder.addOffset(4, parametersOffset, 0); }
  public static int createParametersVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startParametersVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endCommandFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

