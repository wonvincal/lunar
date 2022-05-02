package com.lunar.util;

//public class SbeDirectBufferAdapter extends DirectBuffer {
//	private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
//	private org.agrona.DirectBuffer aeronBuffer;
//	
//	/**
//	 * Call one of the parent's constructor because DirectBuffer doesn't
//	 * have a default constructor 
//	 * @param buffer
//	 */
//	public SbeDirectBufferAdapter(org.agrona.DirectBuffer buffer){
//		super(buffer.byteArray());
//		this.aeronBuffer = buffer;
//	}
//
//    public void wrap(org.agrona.DirectBuffer buffer){
//    	this.aeronBuffer = buffer;
//    }
//    
//    @Override
//    public byte[] array() {
//    	return aeronBuffer.byteArray();
//    }
//    
//    @Override
//    public int capacity() {
//    	return aeronBuffer.capacity();
//    }
//    
//    @Override
//    public ByteBuffer byteBuffer() {
//    	return aeronBuffer.byteBuffer();
//    }
//    
//    @Override
//    public void checkLimit(int limit) {
//    	aeronBuffer.boundsCheck(0, limit);
//    }
//    
//    @Override
//    public ByteBuffer duplicateByteBuffer() {
//    	return aeronBuffer.byteBuffer().duplicate();
//    }
//    
//    @Override
//    public byte getByte(int index) {
//    	return aeronBuffer.getByte(index);
//    }
//    
//    @Override
//    public int getBytes(int index, byte[] dst) {
//    	aeronBuffer.getBytes(index, dst);
//    	return dst.length;
//    }
//    
//    @Override
//    public int getBytes(int index, byte[] dst, int offset, int length) {
//    	aeronBuffer.getBytes(index, dst, offset, length);
//    	return dst.length;
//    }
//    
//    @Override
//    public int getBytes(int index, ByteBuffer dstBuffer, int length) {
//    	aeronBuffer.getBytes(index, dstBuffer, length);
//    	return length;
//    }
//    
//    @Override
//    public int getBytes(int index, DirectBuffer dst, int offset, int length) {
//    	//MutableDirectBuffer mutable = MutableDirectBuffer
//    	throw new NotImplementedException();
//    	
////    	aeronBuffer.getBytes(index, dst.byteBuffer(), offset, length);
//    }
//    
//    @Override
//    public double getDouble(int index, ByteOrder byteOrder) {
//    	return aeronBuffer.getDouble(index, byteOrder);
//    }
//    
//    @Override
//    public float getFloat(int index, ByteOrder byteOrder) {
//    	return aeronBuffer.getFloat(index, byteOrder);
//    }
//    
//    @Override
//    public int getInt(int index, ByteOrder byteOrder) {
//    	return aeronBuffer.getInt(index, byteOrder);
//    }
//    
//    @Override
//    public long getLong(int index, ByteOrder byteOrder) {
//    	return aeronBuffer.getLong(index, byteOrder);
//    }
//    
//    @Override
//    public short getShort(int index, ByteOrder byteOrder) {
//    	return aeronBuffer.getShort(index, byteOrder);
//    }
//    
//    @Override
//    public void putByte(int index, byte value) {
//    	throw new NotImplementedException();
//    }
//    
//    @Override
//    public int putBytes(int index, byte[] src) {
//    	throw new NotImplementedException();
//    }
//
//    @Override
//    public int putBytes(int index, byte[] src, int offset, int length) {
//    	throw new NotImplementedException();
//    }
//    
//    @Override
//    public int putBytes(int index, ByteBuffer srcBuffer, int length) {
//    	throw new NotImplementedException();
//    }
//
//    @Override
//    public int putBytes(int index, DirectBuffer src, int offset, int length) {
//    	throw new NotImplementedException();
//    }
//    
//    @Override
//    public void putDouble(int index, double value, ByteOrder byteOrder) {
//    	throw new NotImplementedException();
//    }
//    
//    @Override
//    public void putFloat(int index, float value, ByteOrder byteOrder) {
//    	throw new NotImplementedException();
//    }
//    
//    @Override
//    public void putInt(int index, int value, ByteOrder byteOrder) {
//    	throw new NotImplementedException();
//    }
//    
//    @Override
//    public void putLong(int index, long value, ByteOrder byteOrder) {
//    	throw new NotImplementedException();
//    }
//    
//    @Override
//    public void putShort(int index, short value, ByteOrder byteOrder) {
//    	throw new NotImplementedException();
//    }
//}
