// automatically generated by the FlatBuffers compiler, do not modify

package com.lunar.message.io.fbs;

import java.nio.*;
import java.lang.*;
import java.util.*;
import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class ServiceStatusFbs extends Table {
  public static ServiceStatusFbs getRootAsServiceStatusFbs(ByteBuffer _bb) { return getRootAsServiceStatusFbs(_bb, new ServiceStatusFbs()); }
  public static ServiceStatusFbs getRootAsServiceStatusFbs(ByteBuffer _bb, ServiceStatusFbs obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; }
  public ServiceStatusFbs __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  public byte systemId() { int o = __offset(4); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte sinkId() { int o = __offset(6); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte serviceType() { int o = __offset(8); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public byte serviceStatusType() { int o = __offset(10); return o != 0 ? bb.get(o + bb_pos) : 0; }
  public long modifyTimeAtOrigin() { int o = __offset(12); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public long sentTime() { int o = __offset(14); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }
  public long healthCheckTime() { int o = __offset(16); return o != 0 ? bb.getLong(o + bb_pos) : 0L; }

  public static int createServiceStatusFbs(FlatBufferBuilder builder,
      byte systemId,
      byte sinkId,
      byte serviceType,
      byte serviceStatusType,
      long modifyTimeAtOrigin,
      long sentTime,
      long healthCheckTime) {
    builder.startObject(7);
    ServiceStatusFbs.addHealthCheckTime(builder, healthCheckTime);
    ServiceStatusFbs.addSentTime(builder, sentTime);
    ServiceStatusFbs.addModifyTimeAtOrigin(builder, modifyTimeAtOrigin);
    ServiceStatusFbs.addServiceStatusType(builder, serviceStatusType);
    ServiceStatusFbs.addServiceType(builder, serviceType);
    ServiceStatusFbs.addSinkId(builder, sinkId);
    ServiceStatusFbs.addSystemId(builder, systemId);
    return ServiceStatusFbs.endServiceStatusFbs(builder);
  }

  public static void startServiceStatusFbs(FlatBufferBuilder builder) { builder.startObject(7); }
  public static void addSystemId(FlatBufferBuilder builder, byte systemId) { builder.addByte(0, systemId, 0); }
  public static void addSinkId(FlatBufferBuilder builder, byte sinkId) { builder.addByte(1, sinkId, 0); }
  public static void addServiceType(FlatBufferBuilder builder, byte serviceType) { builder.addByte(2, serviceType, 0); }
  public static void addServiceStatusType(FlatBufferBuilder builder, byte serviceStatusType) { builder.addByte(3, serviceStatusType, 0); }
  public static void addModifyTimeAtOrigin(FlatBufferBuilder builder, long modifyTimeAtOrigin) { builder.addLong(4, modifyTimeAtOrigin, 0L); }
  public static void addSentTime(FlatBufferBuilder builder, long sentTime) { builder.addLong(5, sentTime, 0L); }
  public static void addHealthCheckTime(FlatBufferBuilder builder, long healthCheckTime) { builder.addLong(6, healthCheckTime, 0L); }
  public static int endServiceStatusFbs(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
}
