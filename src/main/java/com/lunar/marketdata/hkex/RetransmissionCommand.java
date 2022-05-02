package com.lunar.marketdata.hkex;

public class RetransmissionCommand {
	private RetransmissionCommandType commandType;
	private int channelId;
	private long beginSeq;
	private long endSeq;
	
	public static RetransmissionCommand of(){
		return new RetransmissionCommand();
	}
	public void set(RetransmissionCommandType commandType, int channelId, long beginSeq, long endSeq){
		this.commandType = commandType;
		this.channelId = channelId;
		this.beginSeq = beginSeq;
		this.endSeq = endSeq;
	}
	public void set(RetransmissionCommandType commandType){
		this.commandType = commandType;
		this.channelId = -1;
		this.beginSeq = -1;
		this.endSeq = -1;
	}
	public RetransmissionCommandType commandType(){ return commandType; }
	public int channelId(){ return channelId; }
	public long beginSeq(){ return beginSeq; }
	public long endSeq(){ return endSeq; }
}
