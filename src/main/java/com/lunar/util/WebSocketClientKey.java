package com.lunar.util;

import io.undertow.websockets.core.WebSocketChannel;

public class WebSocketClientKey implements ClientKey {
	private int clientKey;
	private WebSocketChannel webSocketChannel;
	public static WebSocketClientKey of(int clientKey, WebSocketChannel webSocketChannel){
		return new WebSocketClientKey(clientKey, webSocketChannel);
	}
	WebSocketClientKey(int clientKey, WebSocketChannel webSocketChannel){
		this.clientKey = clientKey;
		this.webSocketChannel = webSocketChannel;
	}
	
	@Override
	public int key(){ return this.clientKey;}
	
	public WebSocketChannel webSocketChannel(){ return this.webSocketChannel;}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + clientKey;
		result = prime * result + ((webSocketChannel.getSourceAddress() == null) ? 0 : webSocketChannel.getSourceAddress().hashCode());
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
		WebSocketClientKey other = (WebSocketClientKey) obj;
		if (clientKey != other.clientKey)
			return false;
		if (webSocketChannel == null) {
			if (other.webSocketChannel != null)
				return false;
		} else if (!webSocketChannel.getSourceAddress().equals(other.webSocketChannel.getSourceAddress()))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "clientKey:" + clientKey + ", sourceAddress:" + ((webSocketChannel != null)? webSocketChannel.getSourceAddress() : "null");
	}
}
