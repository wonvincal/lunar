package com.lunar.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.core.Lifecycle;
import com.lunar.core.Lifecycle.LifecycleStateHook;
import com.lunar.core.LifecycleState;

public class LifecycleController {
	static final Logger LOG = LogManager.getLogger(LifecycleController.class);
	private final AtomicReference<LifecycleState> state;
	private final LifecycleStateHook handler;
	private final String name;
	
	public static LifecycleController of(String name){
		return new LifecycleController(name, LifecycleStateHook.NULL_HANDLER);
	}
	
	public static LifecycleController of(String name, LifecycleStateHook handler){
		return new LifecycleController(name, handler);
	}

	LifecycleController(String name, LifecycleStateHook handler){
		this.name = name;
		this.state = new AtomicReference<LifecycleState>(LifecycleState.INIT);
		this.handler = handler;
	}
	
	public LifecycleState state(){
		return state.get();
	}
	
	LifecycleController state(LifecycleState state){
		this.state.set(state);
		return this;
	}

	private BiConsumer<Boolean, Throwable> consumeThenComplete(CompletableFuture<LifecycleState> future, LifecycleState expectedCurrent, LifecycleState next){
		return new BiConsumer<Boolean, Throwable>() {
			@Override
			public void accept(Boolean value, Throwable throwable) {
				if (throwable != null){
					LOG.warn("Caught throwable when pending to move to next state [name:" + name + ", expected:" + expectedCurrent.name() + ", next:" + next.name() + "]", throwable);
					future.completeExceptionally(Lifecycle.stateTransitionExceptionOf(expectedCurrent, next, throwable));
					return;
				}
				if  (!value.booleanValue()){
					LOG.warn("Pending {} returned false [name:{}, expected:{}, actual:{}, next:{}]",
							next.name(),
							name,
							expectedCurrent.name(), 
							state.get().name(), 
							next.name());
					future.completeExceptionally(Lifecycle.stateTransitionExceptionOf(expectedCurrent, next));
					return;
				}
				try {
					switch (expectedCurrent){
					case ACTIVE:
						handler.onActiveExit();
						break;
					case RESET:
						handler.onResetExit();
						break;
					case RECOVERY:
						handler.onRecoveryExit();
						break;
					case WARMUP:
						handler.onWarmupExit();
						break;
					case INIT:
						// noop
						break;
					default:
						future.completeExceptionally(Lifecycle.stateTransitionExceptionOf(expectedCurrent, state.get()));
						return;
					}
				}
				catch (Exception ex){
					LOG.error("Caught exception during transition [name:{}, currentState:{}, nextState:{}]", name, expectedCurrent, state.get());
					// TODO TODO This is going to be a big problem
					future.completeExceptionally(Lifecycle.stateTransitionExceptionOf(expectedCurrent, state.get()));
					return;
				}

				if (!state.compareAndSet(expectedCurrent, next)){
					LOG.error("State has been changed unexpectedly [name:{}, expected:{}, actual:{}, next:{}]",
							name,
							expectedCurrent.name(), 
							state.get().name(), 
							next.name());
					future.completeExceptionally(Lifecycle.concurrentTransitionExceptionOf(expectedCurrent, state.get(), next));
					return;
				}
				LOG.info("State changed successfully [name:{}, currentState:{}, prevState:{}]", name, state.get().name(), expectedCurrent);
				// Change state
				try {
					switch (state.get()){
					case ACTIVE:
						handler.onActiveEnter();
						break;
					case RESET:
						handler.onResetEnter();
						break;
					case RECOVERY:
						handler.onRecoveryEnter();
						break;
					case STOPPED:
						handler.onStopped();
						break;
					case WARMUP:
						handler.onWarmupEnter();
						break;
					default:
						break;
					}
				}
				catch (Exception ex){
					LOG.error("Caught exception during transition [name:{}, nextState:{}]", name, state.get());
					// TODO TODO This is going to be a big problem
					future.completeExceptionally(Lifecycle.stateTransitionExceptionOf(expectedCurrent, state.get()));
					return;
				}
				future.complete(state.get());
			}
		};
	}
	
	public CompletableFuture<LifecycleState> active(){
		return transitionTo(LifecycleState.ACTIVE);
	}
	
	public CompletableFuture<LifecycleState> reset(){
		return transitionTo(LifecycleState.RESET);
	}
	
	public CompletableFuture<LifecycleState> warmup(){
		return transitionTo(LifecycleState.WARMUP);
	}

	public CompletableFuture<LifecycleState> recover(){
		return transitionTo(LifecycleState.RECOVERY);
	}

	public CompletableFuture<LifecycleState> stop(){
		return transitionTo(LifecycleState.STOPPED);
	}
	
	/**
	 * Move to next state.  Complete future exceptionally if transition is not allowed.
	 * Complete future right away if transition is to current state (no transition).
	 * @param next
	 * @return
	 */
	private CompletableFuture<LifecycleState> transitionTo(final LifecycleState next){
		final LifecycleState current = state.get();
		if (next == current){
			return CompletableFuture.completedFuture(current);
		}
		
		boolean allow = false;
		switch (current){
		case INIT:
			// Only RECOVERY, WARMUP, ACTIVE is allow
			allow = (next == LifecycleState.ACTIVE || next == LifecycleState.RECOVERY || next == LifecycleState.WARMUP);
			if (allow){
				handler.onInit();
			}
			break;
		case WARMUP:
			allow = (next == LifecycleState.RESET);
			break;
		case RECOVERY:
			allow = (next == LifecycleState.ACTIVE || next == LifecycleState.RESET);
			break;
		case ACTIVE:
			allow = (next == LifecycleState.RESET);
			break;
		case RESET:
			allow = (next == LifecycleState.RECOVERY || next == LifecycleState.ACTIVE || next == LifecycleState.STOPPED);
			break;
		case STOPPED:
			allow = false;
		default:
			break;
		}
		if (!allow){
			LOG.error("Transition not allow [name:{}, currentState:{}, nextState:{}]", name, current, next);
			return Lifecycle.invalidStateTransition(current, next);
		}
		
		final CompletableFuture<LifecycleState> future = new CompletableFuture<LifecycleState>();
		switch (next){
		case ACTIVE:
			handler.onPendingActive().whenComplete(consumeThenComplete(future, current, next));
			break;
		case RESET:
			handler.onPendingReset().whenComplete(consumeThenComplete(future, current, next));
			break;
		case RECOVERY:
			handler.onPendingRecovery().whenComplete(consumeThenComplete(future, current, next));
			break;
		case WARMUP:
			handler.onPendingWarmup().whenComplete(consumeThenComplete(future, current, next));
			break;
		case STOPPED:
			handler.onPendingStop().whenComplete(consumeThenComplete(future, current, next));
			break;
		default:
			if (!state.compareAndSet(current, next)){
				LOG.error("State has been changed unexpectedly [name:{}, expected:{}, actual:{}, next:{}]",
						name,
						current.name(), 
						state.get().name(), 
						next.name());
				future.completeExceptionally(Lifecycle.concurrentTransitionExceptionOf(current, state.get(), next));
			}
			future.complete(state.get());
			break;
		}
		return future;
	}

}
