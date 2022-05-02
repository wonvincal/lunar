package com.lunar.message;

import com.lunar.message.io.sbe.ResponseSbeDecoder;

import org.agrona.DirectBuffer;

public interface ResponseHandler {

	/**
	 * 
	 * @param response
	 * @param messageCodec
	 * @param messageBuffer
	 * @return true if the response has been handled successfully and its owning tracker object
	 *         can remove the related request from its cache
	 */
	void handleResponse(Response response);
	
	/**
	 * 
	 * @param response
	 * @param messageCodec
	 * @param messageBuffer
	 * @return true if the response has been handled successfully and its owning tracker object
	 *         can remove the related request from its cache
	 */
	void handleResponse(DirectBuffer buffer, int offset, ResponseSbeDecoder response);

	public static ResponseHandler NULL_HANDLER = new ResponseHandler() {
		@Override
		public void handleResponse(DirectBuffer buffer, int offset, ResponseSbeDecoder response) {
		}
		
		@Override
		public void handleResponse(Response response){
		}
	};
}
