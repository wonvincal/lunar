package com.lunar.message.sender;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.entity.ChartData;
import com.lunar.entity.ChartData.VolumeAtPrice;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.ChartDataSbeEncoder;
import com.lunar.message.io.sbe.ChartDataSbeEncoder.VolAtPricesEncoder;
import com.lunar.message.io.sbe.MessageHeaderEncoder;
import com.lunar.message.io.sbe.VolumeClusterDirectionType;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkBufferClaim;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

public class ChartDataSender {
	static final Logger LOG = LogManager.getLogger(ChartDataSender.class);

	private final MessageSender msgSender;
	private final ChartDataSbeEncoder sbe = new ChartDataSbeEncoder();

	public static ChartDataSender of(MessageSender msgSender){
		return new ChartDataSender(msgSender);
	}
	
	ChartDataSender(MessageSender msgSender){
		this.msgSender = msgSender;
	}
	
	public static int expectedEncodedLength(int numVolPrices){
		int size = encodedLength(numVolPrices);
		if (size > ServiceConstant.MAX_MESSAGE_SIZE){
			throw new IllegalArgumentException("Block length (" + MessageHeaderEncoder.ENCODED_LENGTH + ChartDataSbeEncoder.BLOCK_LENGTH + ") exceeds max allowable size (" + ServiceConstant.MAX_MESSAGE_SIZE + ")");
		}
		return size;
	}
	
	public static int encodedLength(int numVolPrices){
		int total = MessageHeaderEncoder.ENCODED_LENGTH + 
				ChartDataSbeEncoder.BLOCK_LENGTH +
				ChartDataSbeEncoder.VolAtPricesEncoder.sbeHeaderSize() +
				ChartDataSbeEncoder.VolAtPricesEncoder.sbeBlockLength() * numVolPrices;
		return total;
	}

	public long sendChartData(MessageSinkRef sink,
			ChartData entity){
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(entity.numVolClusterPrices()), sink, bufferClaim) >= 0L){
			encodeChartData(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe,
					entity);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeChartData(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, 
				entity);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public static int encodeChartData(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			ChartDataSbeEncoder sbe,
			ChartData entity){
		int payloadLength = encodeChartDataOnly(buffer, 
				offset + sender.headerSize(), 
				sbe,
				entity);
		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				ChartDataSbeEncoder.BLOCK_LENGTH, 
				ChartDataSbeEncoder.TEMPLATE_ID, 
				ChartDataSbeEncoder.SCHEMA_ID, 
				ChartDataSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}

	public long sendChartData(MessageSinkRef sink,
			long secSid,
			long dataTime, int windowSizeInSec, 
			int open, int close, int high, int low, int vwap, int valueAreaHigh, int valueAreaLow, VolumeClusterDirectionType volClusterDirType,
			Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices){
		
		MessageSinkBufferClaim bufferClaim = msgSender.bufferClaim();
		if (msgSender.tryClaim(expectedEncodedLength(volumeAtPrices.size()), sink, bufferClaim) >= 0L){
			encodeChartData(msgSender, 
					sink.sinkId(), 
					bufferClaim.buffer(), 
					bufferClaim.offset(), 
					sbe,
					secSid, dataTime, windowSizeInSec, open, close, high, low, vwap, valueAreaHigh, valueAreaLow, volClusterDirType, volumeAtPrices);
			bufferClaim.commit();
			return MessageSink.OK;
		}
		int size = encodeChartData(msgSender, 
				sink.sinkId(), 
				msgSender.buffer(), 
				0, 
				sbe, 
				secSid, dataTime, windowSizeInSec, open, close, high, low, vwap, valueAreaHigh, valueAreaLow, volClusterDirType, volumeAtPrices);
		return msgSender.trySend(sink, msgSender.buffer(), 0, size);
	}
	
	public static int encodeChartData(MessageSender sender,
			int dstSinkId,
			MutableDirectBuffer buffer, 
			int offset,
			ChartDataSbeEncoder sbe,
			long secSid,
			long dataTime, int windowSizeInSec, 
			int open, int close, int high, int low, int vwap, int valueAreaHigh, int valueAreaLow, VolumeClusterDirectionType volClusterDirType,
			Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices){
		int payloadLength = encodeChartDataOnly(buffer, 
				offset + sender.headerSize(), 
				sbe,
				secSid, dataTime, windowSizeInSec, open, close, high, low, vwap, valueAreaHigh, valueAreaLow, volClusterDirType, volumeAtPrices);		
		sender.encodeHeader(dstSinkId,
				buffer,
				offset, 
				ChartDataSbeEncoder.BLOCK_LENGTH, 
				ChartDataSbeEncoder.TEMPLATE_ID, 
				ChartDataSbeEncoder.SCHEMA_ID, 
				ChartDataSbeEncoder.SCHEMA_VERSION,
				payloadLength);
		
		return sender.headerSize() + payloadLength; 
	}

	public static int encodeChartDataOnly(MutableDirectBuffer buffer, 
			int offset,
			ChartDataSbeEncoder sbe,
			long secSid,
			long dataTime, int windowSizeInSec, 
			int open, int close, int high, int low, int vwap, int valueAreaHigh, int valueAreaLow, VolumeClusterDirectionType volClusterDirType,
			Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices){
		VolAtPricesEncoder volAtPricesEncoder = sbe.wrap(buffer, offset)
		.secSid(secSid)
		.dataTime(dataTime)
		.windowSizeInSec(windowSizeInSec)
		.open(open)
		.close(close)
		.high(high)
		.low(low)
		.vwap(vwap)
		.valueAreaHigh(valueAreaHigh)
		.valueAreaLow(valueAreaLow)
		.volClusterDirection(volClusterDirType)
		.volAtPricesCount(volumeAtPrices.size());
		
		for (Entry<VolumeAtPrice> entry : volumeAtPrices.int2ObjectEntrySet()){
			volAtPricesEncoder.next();
			VolumeAtPrice volumeAtPrice = entry.getValue();
			volAtPricesEncoder
				.askQty(volumeAtPrice.askQty())
				.bidQty(volumeAtPrice.bidQty())
				.price(volumeAtPrice.price())
				.significant(volumeAtPrice.significant() ? BooleanType.TRUE : BooleanType.FALSE)
				.volumeClusterType(volumeAtPrice.clusterType());
		}

		return sbe.encodedLength();
	}
	
	public static int encodeChartDataOnly(MutableDirectBuffer buffer, 
			int offset,
			ChartDataSbeEncoder sbe,
			ChartData entity){
		Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices = entity.volumeAtPrices();
		VolAtPricesEncoder volAtPricesEncoder = sbe.wrap(buffer, offset)
		.secSid(entity.secSid())
		.dataTime(entity.dataTime())
		.windowSizeInSec(entity.windowSizeInSec())
		.open(entity.open())
		.close(entity.close())
		.high(entity.high())
		.low(entity.low())
		.vwap(entity.vwap())
		.valueAreaHigh(entity.valueAreaHigh())
		.valueAreaLow(entity.valueAreaLow())
		.volClusterDirection(entity.volClusterDirType())
		.volAtPricesCount(volumeAtPrices.size());
		
		for (Entry<VolumeAtPrice> entry : volumeAtPrices.int2ObjectEntrySet()){
			volAtPricesEncoder.next();
			VolumeAtPrice volumeAtPrice = entry.getValue();
			volAtPricesEncoder
				.askQty(volumeAtPrice.askQty())
				.bidQty(volumeAtPrice.bidQty())
				.price(volumeAtPrice.price())
				.significant(volumeAtPrice.significant() ? BooleanType.TRUE : BooleanType.FALSE)
				.volumeClusterType(volumeAtPrice.clusterType());
		}
		
		return sbe.encodedLength();
	}
}
