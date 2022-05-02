package com.lunar.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.exception.StateTransitionException;
import com.lunar.service.LifecycleController;

/**
 * Lifecycle with the following states:
 * 1) INIT
 * 2) ACTIVE
 * 3) WARMUP
 * 4) RECOVERY
 * 5) IDLE
 * @author wongca
 *
 */
public interface Lifecycle {
	LifecycleController controller();
	
	public interface LifecycleStateHook {
		default void onInit(){}
		default CompletableFuture<Boolean> onPendingActive(){
			return CompletableFuture.completedFuture(true);
		}
		default void onActiveEnter(){}
		default void onActiveExit(){}
		default CompletableFuture<Boolean> onPendingRecovery(){
			return CompletableFuture.completedFuture(true);
		}
		default void onRecoveryEnter(){}
		default void onRecoveryExit(){}
		default CompletableFuture<Boolean> onPendingStop(){
			return CompletableFuture.completedFuture(true);
		}
		default void onStopped(){}
		default CompletableFuture<Boolean> onPendingReset(){
			return CompletableFuture.completedFuture(true);
		}
		default void onResetEnter(){}
		default void onResetExit(){}
		default CompletableFuture<Boolean> onPendingWarmup(){
			return CompletableFuture.completedFuture(true);
		}
		default void onWarmupEnter(){}
		default void onWarmupExit(){}
		
		public static LifecycleStateHook NULL_HANDLER = new LifecycleStateHook(){

			@Override
			public CompletableFuture<Boolean> onPendingActive() {
				return CompletableFuture.completedFuture(true);
			}

			@Override
			public CompletableFuture<Boolean> onPendingRecovery() {
				return CompletableFuture.completedFuture(true);
			}

			@Override
			public CompletableFuture<Boolean> onPendingReset() {
				return CompletableFuture.completedFuture(true);
			}

			@Override
			public CompletableFuture<Boolean> onPendingWarmup() {
				return CompletableFuture.completedFuture(true);
			}

			@Override
			public CompletableFuture<Boolean> onPendingStop() {
				return CompletableFuture.completedFuture(true);
			}
		};
	}
	
	public interface LifecycleStateChangeHandlerObsolete {
		void handle(LifecycleState prev, LifecycleState current);
		
		public static LifecycleStateChangeHandlerObsolete NULL_HANDLER = new LifecycleStateChangeHandlerObsolete() {
			private final Logger LOG = LogManager.getLogger(LifecycleStateChangeHandlerObsolete.class);
			
			@Override
			public void handle(LifecycleState prev, LifecycleState current) {
				LOG.error("Receive state change in null handler");
			}
		};
	}
	
	public interface LifecycleExceptionHandler {
		/**
		 * 
		 * @param e
		 * @return false if stop processing
		 */
		boolean handle(LifecycleState current, Throwable e);
		void handleOnStartException(Throwable e);
		void handleOnShutdownException(Throwable e);
		
		public static LifecycleExceptionHandler DEFAULT_HANDLER = new LifecycleExceptionHandler() {
			private final Logger LOG = LogManager.getLogger(LifecycleExceptionHandler.class);
			
			@Override
			public boolean handle(LifecycleState current, Throwable e) {
				LOG.error("Caught exception [state:" + current.name() + "]", e);
				return false;
			}

			@Override
			public void handleOnStartException(Throwable e) {
				LOG.error("Caught exception on start", e);
			}

			@Override
			public void handleOnShutdownException(Throwable e) {
				LOG.error("Caught exception on shutdown", e);
			}
		};
	}
	
	String name();
	
	default LifecycleState state(){
		return controller().state();
	}
	
	/**
	 * Start the lifecycle.  If all lifecycle implementations are being started from Disruptor and Executor level,
	 * we can safely remove this method from the interface.
	 * 
	 * TODO Report on timeout as well
	 */
	default CompletableFuture<LifecycleState> active(){
		return controller().active();
	}
	
	default CompletableFuture<LifecycleState> reset(){
		return controller().reset();
	}
	
	default CompletableFuture<LifecycleState> warmup(){
		return controller().warmup();
	}
	
	default CompletableFuture<LifecycleState> recover(){
		return controller().recover();
	}
	
	default CompletableFuture<LifecycleState> stop(){
		return controller().stop();
	}
	
	void lifecycleExceptionHandler(LifecycleExceptionHandler handler);
	
	public static CompletableFuture<LifecycleState> invalidStateTransition(LifecycleState from, LifecycleState to){
		CompletableFuture<LifecycleState> future = new CompletableFuture<LifecycleState>();
		future.completeExceptionally(new StateTransitionException("Invalid state transition [from: " + from.name()  + ", to: " + to.name() + "]"));
		return future;
	}
	
	public static StateTransitionException stateTransitionExceptionOf(LifecycleState from, LifecycleState to){
		return new StateTransitionException("Invalid state transition [from: " + from.name()  + ", to: " + to.name() + "]");
	}

	public static StateTransitionException stateTransitionExceptionOf(LifecycleState from, LifecycleState to, Throwable cause){
		return new StateTransitionException("Invalid state transition [from: " + from.name()  + ", to: " + to.name() + "]", cause);
	}

	public static StateTransitionException concurrentTransitionExceptionOf(LifecycleState expectedFrom, LifecycleState to, LifecycleState actualFrom){
		return new StateTransitionException("Concurrent state transition [expectedFrom:" + expectedFrom.name() + ", actualFrom:" + actualFrom.name() + ", to: " + to.name() + "]");
	}

	public static BiConsumer<LifecycleState, Throwable> consumeThenComplete(CompletableFuture<Boolean> future, boolean valueIfSuccess){
		return new BiConsumer<LifecycleState, Throwable>() {
			@Override
			public void accept(LifecycleState value, Throwable cause) {
				if (cause != null){
					future.completeExceptionally(cause);
					return;
				}
				future.complete(valueIfSuccess);
			}
		};
	}
}
