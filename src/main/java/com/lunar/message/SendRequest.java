package com.lunar.message;

public class SendRequest {
	private final Request request;
	public SendRequest(Request request){
		this.request = request;
	}
	public Request request(){
		return this.request;
	}
}
