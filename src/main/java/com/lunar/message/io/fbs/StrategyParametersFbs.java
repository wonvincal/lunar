// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class StrategyParametersFbs extends Table {
  public static StrategyParametersFbs getRootAsStrategyParametersFbs(ByteBuffer _bb) { return getRootAsStrategyParametersFbs(_bb, new StrategyParametersFbs()); }
  public static StrategyParametersFbs getRootAsStrategyParametersFbs(ByteBuffer _bb, StrategyParametersFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public StrategyParametersFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public long strategyId() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public StrategyParamValueFbs parameters(int j) { return parameters(new StrategyParamValueFbs(), j); }
  public StrategyParamValueFbs parameters(StrategyParamValueFbs obj, int j) { int o = __offset(6); return o != 0 ? obj.__assign(__indirect(__vector(o) + j * 4), bb) : null; }
  public int parametersLength() { int o = __offset(6); return o != 0 ? __vector_len(o) : 0; }

  public static int createStrategyParametersFbs(FlatBufferBuilder builder,
      long strategyId,
      int parametersOffset) {
    builder.startObject(2);
    StrategyParametersFbs.addStrategyId(builder, strategyId);
    StrategyParametersFbs.addParameters(builder, parametersOffset);
    return StrategyParametersFbs.endStrategyParametersFbs(builder);
  }

  public static void startStrategyParametersFbs(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addStrategyId(FlatBufferBuilder builder, long strategyId) { builder.addLong(0, strategyId, 0L); }
  public static void addParameters(FlatBufferBuilder builder, int parametersOffset) { builder.addOffset(1, parametersOffset, 0); }
  public static int createParametersVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startParametersVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endStrategyParametersFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}

