package com.lunar.entity;

import java.util.concurrent.atomic.AtomicLong;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.ChartDataSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.io.sbe.VolumeClusterDirectionType;
import com.lunar.message.io.sbe.VolumeClusterType;
import com.lunar.message.sender.ChartDataSender;
import com.lunar.service.ServiceConstant;

import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;

public class ChartData extends Entity {
	static final Logger LOG = LogManager.getLogger(ChartData.class);
	private static int MAX_NUM_VOL_CLUSTER_PRICES = 10;
	private static AtomicLong ENTITY_SID = new AtomicLong(81000000L);
	private long secSid;
	private long dataTime;
	private int windowSizeInSec;
	private int open;
	private int close;
	private int high;
	private int low;
	private int pointOfControl;
	private int valueAreaHigh;
	private int valueAreaLow;
	private int vwap;
	private int[] volClusterPrices;
	private int numVolClusterPrices;
	private VolumeClusterDirectionType volClusterDirType;
	private Int2ObjectRBTreeMap<VolumeAtPrice> volAtPrices;
	
	public static class VolumeAtPrice {
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + price;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			VolumeAtPrice other = (VolumeAtPrice) obj;
			if (price != other.price)
				return false;
			return true;
		}
		private final int price;
		private long bidQty;
		private long askQty;
		private boolean significant;
		private VolumeClusterType clusterType = VolumeClusterType.NORMAL;
		
		public static VolumeAtPrice cloneOf(VolumeAtPrice volAtPrice){
			VolumeAtPrice vol = new VolumeAtPrice(volAtPrice.price);
			vol.bidQty = volAtPrice.bidQty;
			vol.askQty = volAtPrice.askQty;
			vol.significant = volAtPrice.significant;
			vol.clusterType = volAtPrice.clusterType;
			return vol;
		}
		public static VolumeAtPrice ofBidQty(int price, long bidQuantity){
			VolumeAtPrice vol = new VolumeAtPrice(price);
			vol.bidQty = bidQuantity;
			return vol;
		}
		public static VolumeAtPrice ofAskQty(int price, long askQuantity){
			VolumeAtPrice vol = new VolumeAtPrice(price);
			vol.askQty = askQuantity;
			return vol;
		}
		VolumeAtPrice(int price){
			this.bidQty = 0;
			this.askQty = 0;
			this.price = price;
		}
		public int price(){
			return price;
		}
		public long bidQty(){
			return bidQty;
		}
		public long askQty(){
			return askQty;
		}
		public boolean significant(){
			return significant;
		}
		public VolumeClusterType clusterType(){
			return clusterType;
		}
		public void addAskQty(long askQty){
			this.askQty += askQty;
		}
		public void addBidQty(long bidQty){
			this.bidQty += bidQty;
		}
	}

	public static ChartData clone(long secSid, int windowSizeInSec, ChartData data){
		return new ChartData(secSid, 
				data.dataTime, 
				windowSizeInSec, 
				data.open, 
				data.close, 
				data.high, 
				data.low, 
				data.pointOfControl, 
				data.valueAreaHigh, 
				data.valueAreaLow,
				data.vwap,
				data.volClusterDirType);
	}
	
	public static ChartData of(long secSid, int windowSizeInSec){
		return new ChartData(secSid, 
				ServiceConstant.NULL_TIME_NS, 
				windowSizeInSec, 
				ServiceConstant.NULL_CHART_PRICE, 
				ServiceConstant.NULL_CHART_PRICE, 
				ServiceConstant.NULL_CHART_PRICE, 
				ServiceConstant.NULL_CHART_PRICE, 
				ServiceConstant.NULL_CHART_PRICE, 
				ServiceConstant.NULL_CHART_PRICE, 
				ServiceConstant.NULL_CHART_PRICE, 
				ServiceConstant.NULL_CHART_PRICE,
				VolumeClusterDirectionType.NULL_VAL);
	}
	
	public static ChartData of(long secSid, long dataTime, int windowSizeInSec, int open, int close, int high, int low,
			int pointOfControl, int valueAreaLow, int valueAreaHigh, int vwap, VolumeClusterDirectionType volClusterDirType){
		return new ChartData(secSid, dataTime, windowSizeInSec, open, close, high, low, pointOfControl, valueAreaLow, valueAreaHigh, vwap, volClusterDirType);		
	}
	
	ChartData(long secSid, long dataTime, int windowSizeInSec, int open, int close, int high, int low,
			int pointOfControl, int valueAreaLow, int valueAreaHigh, int vwap, VolumeClusterDirectionType volClusterDirType) {
		super(ENTITY_SID.getAndIncrement());
		this.secSid = secSid;
		this.dataTime =  dataTime;
		this.windowSizeInSec = windowSizeInSec;
		this.open = open;
		this.close = close;
		this.high = high;
		this.low = low;
		this.pointOfControl = pointOfControl;
		this.valueAreaLow = valueAreaLow;
		this.valueAreaHigh = valueAreaHigh;
		this.vwap = vwap;
		this.numVolClusterPrices = 0;
		this.volClusterPrices = new int[MAX_NUM_VOL_CLUSTER_PRICES];
		this.volAtPrices = new Int2ObjectRBTreeMap<>();
		this.volClusterDirType = volClusterDirType;
	}

	public void clear(){
		this.dataTime =  ServiceConstant.NULL_TIME_NS;
		this.open = ServiceConstant.NULL_CHART_PRICE;
		this.close = ServiceConstant.NULL_CHART_PRICE;
		this.high = ServiceConstant.NULL_CHART_PRICE;
		this.low = ServiceConstant.NULL_CHART_PRICE;
		this.pointOfControl = ServiceConstant.NULL_CHART_PRICE;
		this.valueAreaHigh = ServiceConstant.NULL_CHART_PRICE;
		this.valueAreaLow = ServiceConstant.NULL_CHART_PRICE;
		this.vwap = ServiceConstant.NULL_CHART_PRICE;
		this.numVolClusterPrices = 0;
		this.volAtPrices.clear();
	}
	
	public long secSid(){
		return secSid;
	}
	
	public ChartData secSid(long secSid){
		this.secSid = secSid;
		return this;
	}

	public int windowSizeInSec(){
		return windowSizeInSec;
	}
	
	public ChartData windowSizeInSec(int windowSizeInSec){
		this.windowSizeInSec = windowSizeInSec;
		return this;
	}
	
	public long dataTime(){
		return dataTime;
	}
	
	public ChartData dataTime(long dataTime){
		this.dataTime = dataTime;
		return this;
	}

	public int open(){
		return open;
	}
	
	public ChartData open(int open){
		this.open = open;
		return this;
	}

	public int close(){
		return close;
	}
	
	public ChartData close(int close){
		this.close = close;
		return this;
	}

	public int high(){
		return high;
	}
	
	public ChartData high(int high){
		this.high = high;
		return this;
	}

	public int low(){
		return low;
	}
	
	public ChartData low(int low){
		this.low = low;
		return this;
	}

	public int pointOfControl(){
		return pointOfControl;
	}
	
	public ChartData pointOfControl(int pointOfControl){
		this.pointOfControl = pointOfControl;
		return this;
	}

	public int valueAreaHigh(){
		return valueAreaHigh;
	}
	
	public ChartData valueAreaHigh(int valueAreaHigh){
		this.valueAreaHigh = valueAreaHigh;
		return this;
	}

	public int valueAreaLow(){
		return valueAreaLow;
	}
	
	public ChartData valueAreaLow(int valueAreaLow){
		this.valueAreaLow = valueAreaLow;
		return this;
	}

	public int vwap(){
		return vwap;
	}
	
	public ChartData vwap(int vwap){
		this.vwap = vwap;
		return this;
	}

	public VolumeClusterDirectionType volClusterDirType(){
		return volClusterDirType;
	}
	
	public ChartData volClusterDirType(VolumeClusterDirectionType value){
		this.volClusterDirType = value;
		return this;
	}

	public int[] volClusterPrices(){
		return volClusterPrices;
	}
	
	public ChartData volClusterPrices(int index, int price){
		this.volClusterPrices[index] = price;
		return this;
	}
	
	public int numVolClusterPrices(){
		return numVolClusterPrices;
	}

	public ChartData numVolClusterPrices(int numVolClusterPrices){
		this.numVolClusterPrices = numVolClusterPrices;
		return this;
	}

	public Int2ObjectRBTreeMap<VolumeAtPrice> volumeAtPrices(){
		return volAtPrices;
	}
	
	public void significant(int price, boolean value){
		VolumeAtPrice volumeAtPrice = this.volAtPrices.get(price);
		if (volumeAtPrice != this.volAtPrices.defaultReturnValue()){
			volumeAtPrice.significant = value;
		}
		else {
			LOG.error("Not allow to set signficance when bid and ask quantity is not known");
		}		
	}
	
	public void volumeClusterType(int price, VolumeClusterType clusterType){
		VolumeAtPrice volumeAtPrice = this.volAtPrices.get(price);
		if (volumeAtPrice != this.volAtPrices.defaultReturnValue()){
			volumeAtPrice.clusterType = clusterType;
		}
		else {
			LOG.error("Not allow to set clusterType when bid and ask quantity is not known");
		}
	}

	public void addVolAtPrice(VolumeAtPrice volAtPrice){
		VolumeAtPrice volumeAtPrice = this.volAtPrices.get(volAtPrice.price());
		if (volumeAtPrice != this.volAtPrices.defaultReturnValue()){
			volumeAtPrice.bidQty += volAtPrice.bidQty;
			volumeAtPrice.askQty += volAtPrice.askQty;			
		}
		else {
			volumeAtPrice = VolumeAtPrice.cloneOf(volAtPrice);
			volAtPrices.put(volAtPrice.price(), volumeAtPrice);
		}		
	}
	
	public void addBidQty(int price, long quantity){
		VolumeAtPrice volumeAtPrice = this.volAtPrices.get(price);
		if (volumeAtPrice != this.volAtPrices.defaultReturnValue()){
			volumeAtPrice.bidQty += quantity;
		}
		else {
			volAtPrices.put(price, VolumeAtPrice.ofBidQty(price, quantity));
		}
	}
	
	public void addAskQty(int price, long quantity){
		VolumeAtPrice volumeAtPrice = this.volAtPrices.get(price);
		if (volumeAtPrice != this.volAtPrices.defaultReturnValue()){
			volumeAtPrice.askQty += quantity;
		}
		else {
			volAtPrices.put(price, VolumeAtPrice.ofAskQty(price, quantity));
		}
	}

	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return ChartDataSender.encodeChartDataOnly(buffer, offset, encoder.chartDataSbeEncoder(), this);
	}

	@Override
	public TemplateType templateType() {
		return TemplateType.CHART_DATA;
	}

	@Override
	public short blockLength() {
		return ChartDataSbeEncoder.BLOCK_LENGTH;
	}

	@Override
	public int expectedEncodedLength() {
		return ChartDataSbeEncoder.BLOCK_LENGTH + ChartDataSbeEncoder.VolAtPricesEncoder.sbeHeaderSize() + ChartDataSbeEncoder.VolAtPricesEncoder.sbeBlockLength() * numVolClusterPrices;
	}

	@Override
	public int schemaId() {
		return ChartDataSbeEncoder.SCHEMA_ID;
	}

	@Override
	public int schemaVersion() {
		return ChartDataSbeEncoder.SCHEMA_VERSION;
	}	
}
