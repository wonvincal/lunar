package com.lunar.order;

import java.util.concurrent.CompletableFuture;

import com.lunar.core.Lifecycle;
import com.lunar.message.Command;

public interface LineHandlerEngine extends Lifecycle {
	public void sendOrderRequest(OrderRequest orderRequest);
	public void exceptionHandler(LineHandlerEngineExceptionHandler handler);
	public LineHandlerEngine init(OrderUpdateEventProducer producer);
	public int apply(Command command);
    public boolean isClear();
    public CompletableFuture<Boolean> startRecovery();
    public void printOrderExecInfo(int clientOrderId);
    public void printAllOrderExecInfo();
}
