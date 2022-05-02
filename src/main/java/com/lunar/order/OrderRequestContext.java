package com.lunar.order;

import java.util.concurrent.CompletableFuture;

/**
 * Order request for order input.
 * This should be used by the client only
 * @author wongca
 *
 */
public class OrderRequestContext {
	private final OrderRequest request;
	private final CompletableFuture<OrderRequest> result;

	public static OrderRequestContext of(OrderRequest request){
		return new OrderRequestContext(request);
	}
	
	OrderRequestContext(OrderRequest request){
		this.request = request;
		this.result = new CompletableFuture<OrderRequest>();
	}
	
	public OrderRequest request(){
		return request;
	}
	
	public CompletableFuture<OrderRequest> result(){
		return this.result;
	}

}
