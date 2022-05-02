package com.lunar.order;

import com.lunar.exception.StateTransitionException;
import com.lunar.message.io.sbe.OrderAcceptedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderAmendedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderCancelledSbeDecoder;
import com.lunar.message.io.sbe.OrderExpiredSbeDecoder;
import com.lunar.message.io.sbe.OrderRejectedSbeDecoder;
import com.lunar.message.io.sbe.OrderStatus;
import com.lunar.message.io.sbe.TradeSbeDecoder;

/**
 * 
 * @author wongca
 * @deprecated Not a real class.  Just a place to dump all the ideas of FSM for handling order
 */
public class OrderStateMachine {
	public static abstract class State {
		/*
		 * Enter methods
		 */
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, OrderAcceptedSbeDecoder accepted){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, OrderCancelledSbeDecoder cancelled){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, OrderRejectedSbeDecoder rejected){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, OrderExpiredSbeDecoder expired){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, OrderAmendedSbeDecoder amended){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, OrderAmendRejectedSbeDecoder amendRejected){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, OrderCancelRejectedSbeDecoder cancelRejected){
			return Transitions.NO_TRANSITION;
		}
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event, TradeSbeDecoder trade){
			return Transitions.NO_TRANSITION;
		}

		/*
		 * Exit methods
		 */
		State exit(OrderContext orderContext, OrderStateTransitionEvent event){
			return this;
		}
		
		/*
		 * onEvent and on specific input methods
		 */
		Transition onEvent(OrderContext orderContext, OrderStateTransitionEvent event){
			return Transitions.NO_TRANSITION;
		}
		
		Transition onOrderAccepted(OrderContext orderContext, OrderAcceptedSbeDecoder accepted){
			return Transitions.NO_TRANSITION;
		}

		Transition onOrderCancelled(OrderContext orderContext, OrderCancelledSbeDecoder cancelled){
			return Transitions.NO_TRANSITION;
		}

		Transition onOrderRejected(OrderContext orderContext, OrderRejectedSbeDecoder rejected){
			return Transitions.NO_TRANSITION;
		}

		Transition onOrderExpired(OrderContext orderContext, OrderExpiredSbeDecoder expired){
			return Transitions.NO_TRANSITION;
		}

		Transition onOrderAmended(OrderContext orderContext, OrderAmendedSbeDecoder amended){
			return Transitions.NO_TRANSITION;
		}

		Transition onOrderAmendRejected(OrderContext orderContext, OrderAmendRejectedSbeDecoder amendRejected){
			return Transitions.NO_TRANSITION;
		}

		Transition onOrderCancelRejected(OrderContext orderContext, OrderCancelRejectedSbeDecoder cancelRejected){
			return Transitions.NO_TRANSITION;
		}

		Transition onTrade(OrderContext orderContext, TradeSbeDecoder trade){
			return Transitions.NO_TRANSITION;
		}
	}

	/**
	 * These are the general states for each order.  {@link THROTTLED} and {@link FAILED} are lunar 
	 * specific states.  The rest of the states will be determined by messages returned from the 
	 * exchange.  For example, we receive execution report from OCG and order status is specified 
	 * in that message.  For this case, we translate OCG specific order status to one of these states 
	 * below.  
	 * @author Calvin
	 *
	 */
	public static State IDLE = new IdleState();
	public static State PENDING_NEW = new PendingNewState();
	public static State NEW = new NewState();	
	public static State FILLED = new FilledState();		
	public static State PENDING_AMEND = new PendingAmendState();
	public static State PENDING_CANCEL = new PendingCancelState();
	public static State CANCELLED = new CancelledState();
	public static State EXPIRED = new ExpiredState();
	public static State REJECTED = new RejectedState();
	public static State FAILED = new FailedState();
	public static State THROTTLED = new ThrottledState();
	
	public static class FilledState extends State {
		
	}
	public static class CancelledState extends State {
		
	}
	public static class ExpiredState extends State {
		
	}
	public static class RejectedState extends State {
		
	}
	public static class FailedState extends State {
		
	}
	public static class IdleState extends State {

		/**
		 * 6.6.10 - Order Status field of OrderAccepted message must be NEW
		 */
		@Override
		Transition onOrderAccepted(OrderContext orderContext, OrderAcceptedSbeDecoder accepted) {
			orderContext.order().status(accepted.status());
			return onEvent(orderContext, OrderStateTransitionEvent.NEW);
		}
		
		/**
		 * 6.6.10 - Order Status field of OrderRejected message must be REJECTED
		 */
		@Override
		Transition onOrderRejected(OrderContext orderContext, OrderRejectedSbeDecoder rejected) {
			orderContext.order().status(rejected.status());
			return onEvent(orderContext, OrderStateTransitionEvent.REJECTED);
		}
		
		/**
		 * 6.6.10 - Order Status field of OrderCancelRejected message can be:
		 * - NEW
		 * - PARTIALLY FILLED
		 * - FILLED
		 * - CANCELLED
		 * - PENDING CANCEL
		 * - REJECTED
		 * - PENDING NEW
		 * - EXPIRED
		 * - PENDING AMEND
		 * 
		 * The only possible status at this point of an Order is:
		 * - NEW: this is unexpected
		 * - PARTIALLY FILLED: this is unexpected 
		 * - FILLED: rejected because the order has already been filled
		 * - CANCELLED: rejected because the order has already been cancelled
		 * - PENDING_CANCEL: rejected because the order is pending to be cancelled
		 * - REJECTED: rejected because the order has already been rejected
		 * - PENDING_NEW: rejected because the order is pending to be created
		 * - EXPIRED: this is unexpected
		 * - PENDING AMEND: this is unexpected
		 */
//		@Override
//		Transition onOrderCancelRejected(OrderContext orderContext, OrderCancelRejectedSbeDecoder cancelRejected) {
//			switch (cancelRejected.status()){
//			case NEW:
//				break;
//			case PARTIALLY_FILLED:
//				break;
//			case FILLED:
//				break;
//			case CANCELLED:
//				break;
//			case PENDING_CANCEL:
//				break;
//			case REJECTED:
//				break;
//			case PENDING_NEW:
//				return Transitions.NO_TRANSITION;
//			case EXPIRED:
//				break;
//			case PENDING_AMEND:
//				break;
//			default:
//				LOG.error("Received OrderCancelRejected message with unexpected status | " + OrderCancelRejectedDecoder.decodeToString(cancelRejected));
//				return Transitions.in_any_state_go_to_FAILED;
//			}
//		}
		
		/**
		 * 
		 * 6.6.10 - Order Status field of OrderCancelRejected message can be:
		 * - NEW
		 * - PARTIALLY FILLED
		 * - FILLED
		 * - CANCELLED
		 * - PENDING CANCEL
		 * - REJECTED
		 * - PENDING NEW
		 * - EXPIRED
		 * - PENDING AMEND
		 * 
		 */
//		@Override
//		Transition onOrderAmendRejected(OrderContext orderContext, OrderAmendRejectedSbeDecoder amendRejected) {
//			switch (amendRejected.status()){
//			case NEW:
//				break;
//			case PARTIALLY_FILLED:
//				break;
//			case FILLED:
//				break;
//			case CANCELLED:
//				break;
//			case PENDING_CANCEL:
//				break;
//			case REJECTED:
//				break;
//			case PENDING_NEW:
//				return Transitions.NO_TRANSITION;
//			case EXPIRED:
//				break;
//			case PENDING_AMEND:
//				break;
//			default:
//				LOG.error("Received OrderAmendRejected message with unexpected status | " + OrderAmendRejectedDecoder.decodeToString(amendRejected));
//				return Transitions.in_any_state_go_to_FAILED;
//			}
//		}
		
		@Override
		Transition onEvent(OrderContext orderContext, OrderStateTransitionEvent event){
			switch (event){
			case NEW:
				return Transitions.in_IDLE_receive_NEW;
			default:
				return Transitions.in_any_state_go_to_FAILED;
			}
		}

	}

	public static class NewState extends State {
		@Override
		Transition onEvent(OrderContext orderContext, OrderStateTransitionEvent event) {
			switch(event) {
			case AMEND:
				orderContext.order().status(OrderStatus.PENDING_AMEND);
//				orderContext.updateHandler().onUpdated(orderContext.order());
				return Transitions.in_NEW_receive_AMEND;
			case CANCEL:
				orderContext.order().status(OrderStatus.PENDING_CANCEL);
//				orderContext.updateHandler().onUpdated(orderContext.order());
				return Transitions.in_NEW_receive_CANCEL;
			default:
				return Transitions.NO_TRANSITION;
			}
		}
	}

	public static class PendingCancelState extends State {
		
		@Override
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event) {
			if (event != OrderStateTransitionEvent.CANCEL)
				return Transitions.NO_TRANSITION;
			
//			if (!(orderContext.orderInput() instanceof OrderCancel)) {
//				LOG.error("OrderInput is not an instance of OrderCancel. Something has gone seriously wrong.");
//				return Transitions.in_any_state_go_to_FAILED;
//			}
//			
//			OrderCancel orderCancel = (OrderCancel)orderContext.orderInput();
	//
//			// clear the order input
//			orderContext.orderInput(null);

			// TODO: We no longer send orders out from state machine
//			Response response = orderContext.lineHandler().cancel(orderCancel);
//			
//			if (!response.isSuccess()) {
//				// throttled
//				return Transitions.in_PENDING_CANCEL_receive_CANCEL_THROTTLED;
//			}
//			
//			orderContext.addClientOrderId(response.clientOrderId());
			
			// TODO
//			orderContext.timeout(orderContext.timerService().newTimeout(new TimerTask() {
//				@Override
//				public void run(Timeout timeout) throws Exception {
//					// Send a event to myself (i.e. OMES)
//				}
//			}, orderCancel.timeoutAt(), TimeUnit.NANOSECONDS));
//			
			return Transitions.NO_TRANSITION;
		}
		
		@Override
		State exit(OrderContext orderContext, OrderStateTransitionEvent event) {
//			orderContext.cancelTimeout();
			return this;
		}		
	}

	public static class PendingNewState extends State {
		
		@Override
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event) {
			// Only proceed to create a new order if the transition event is CREATE.
			// We could have gotten to this state due to a rejection, in which case
			// we should just return.
			if (event != OrderStateTransitionEvent.CREATE)
				return Transitions.NO_TRANSITION;
			
//			if (!(orderContext.orderInput() instanceof OrderNew)) {
//				LOG.error("OrderInput is not an instance of OrderNew. Something has gone seriously wrong.");
//				return Transitions.in_any_state_go_to_FAILED;
//			}
//			
//			OrderNew orderNew = (OrderNew)orderContext.orderInput();
	//
//			// clear the order input
//			orderContext.orderInput(null);

			// TODO: We no longer send orders out from state machine
//			Response response = orderContext.lineHandler().send(orderNew);
//			
//			if (!response.isSuccess()){
//				// throttled
//				return Transitions.in_PENDING_NEW_receive_CREATE_THROTTLED;
//			}
//			
//			orderContext.addClientOrderId(response.clientOrderId());
			
			// TODO
//			orderContext.timeout(orderContext.timerService().newTimeout(new TimerTask() {
//				@Override
//				public void run(Timeout timeout) throws Exception {
//					// Send a event to myself (i.e. OMES)
//				}
//			}, orderNew.timeoutAt(), TimeUnit.NANOSECONDS));
			
			//TODO: Handle cancel while in pending new
			
			return Transitions.NO_TRANSITION;
		}
		
		@Override
		Transition onEvent(OrderContext orderContext, OrderStateTransitionEvent event) {		
			// TODO: Handle cancel while in pending new here
			return Transitions.NO_TRANSITION;
		}
		
		@Override
		State exit(OrderContext orderContext, OrderStateTransitionEvent event) {
//			orderContext.cancelTimeout();
			return this;
		}
	}

	public static class PendingAmendState extends State {
		
		@Override
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event) {
			if (event != OrderStateTransitionEvent.AMEND)
				return Transitions.NO_TRANSITION;
			
//			if (!(orderContext.orderInput() instanceof OrderAmend)) {
//				LOG.error("OrderInput is not an instance of OrderAmend. Something has gone seriously wrong.");
//				return Transitions.in_any_state_go_to_FAILED;
//			}
//			
//			OrderAmend orderAmend = (OrderAmend)orderContext.orderInput();
//			
//			// clear the order input
//			orderContext.orderInput(null);

			// TODO: Change the logic here, we no longer send out order from state machine
//			Response response = orderContext.lineHandler().amend(orderAmend);
//			
//			if (!response.isSuccess()){
//				// throttled
//				return Transitions.in_PENDING_AMEND_receive_AMEND_THROTTLED;
//			}
			
//			orderContext.order().quantity(orderAmend.quantity()).limitPrice(orderAmend.limitPrice());
//			orderContext.addClientOrderId(response.clientOrderId());
			
			// TODO
//			orderContext.timeout(orderContext.timerService().newTimeout(new TimerTask() {
//				@Override
//				public void run(Timeout timeout) throws Exception {
//					// Send a event to myself (i.e. OMES)
//				}
//			}, orderAmend.timeoutAt(), TimeUnit.NANOSECONDS));			
			
			return Transitions.NO_TRANSITION;
		}
		
		@Override
		State exit(OrderContext orderContext, OrderStateTransitionEvent event) {
//			orderContext.cancelTimeout();
			return this;
		}		
	}

	public static class ThrottledState extends State {
		@Override
		Transition enter(OrderContext orderContext, OrderStateTransitionEvent event) {
			if (event == OrderStateTransitionEvent.CREATE_THROTTLED) {
				// re-send order create message to ourselves through the disruptor
			}
			else if (event == OrderStateTransitionEvent.AMEND_THROTTLED) {
				// re-send order amend message to ourselves through the disruptor
			}	
			else if (event == OrderStateTransitionEvent.CANCEL_THROTTLED) {
				// re-send order cancel message to ourselves through the disruptor
			}
			
			return Transitions.NO_TRANSITION;
		}
		
		@Override
		Transition onEvent(OrderContext orderContext, OrderStateTransitionEvent event) {
			
			switch (event) {
			case CREATE:
				return Transitions.in_THROTTLED_receive_CREATE;
			case AMEND:
				return Transitions.in_THROTTLED_receive_AMEND;
			case CANCEL:
				return Transitions.in_THROTTLED_receive_CANCEL;
			default:
				 return Transitions.in_any_state_go_to_FAILED;
			}
		}
	}

	static class Transition {
		@SuppressWarnings("unused")
		private final OrderStateTransitionEvent event;
		@SuppressWarnings("unused")
		private final State nextState;
		
		static Transition of(State state, OrderStateTransitionEvent event, State nextState){
			return new Transition(state, event, nextState);
		}
		
		static Transition of(OrderStateTransitionEvent event, State nextState){
			return new Transition(null, event, nextState);
		}
		
		Transition(){
			event = null;
			nextState = null;
		}
		
		Transition(State state /* not in use, just for clarity */,
				   OrderStateTransitionEvent event,
				   State nextState){
			this.event = event;
			this.nextState = nextState;
		}
		
		void proceed(OrderContext orderContext) throws StateTransitionException{
//			try {
//				State currentState = orderContext.state();
//				if (currentState != nextState){
//					currentState.exit(orderContext, event);
//					orderContext.state(nextState);
//					nextState.enter(orderContext, event).proceed(orderContext);
//				}
//			}
//			catch (Exception e){
//				throw new StateTransitionException(e);
//			}
		}

		void proceed(OrderContext orderContext, OrderAcceptedSbeDecoder accepted) throws StateTransitionException{
//			try {
//				State currentState = orderContext.state();
//				if (currentState != nextState){
//					currentState.exit(orderContext, event);
//					orderContext.state(nextState);
//					nextState.enter(orderContext, event, accepted).proceed(orderContext);
//				}
//			}
//			catch (Exception e){
//				throw new StateTransitionException(e);
//			}
		}

	}

	static class Transitions{
		static Transition NO_TRANSITION = new Transition(){
			@Override
			public final void proceed(OrderContext orderContext) {}
		};
		
		static Transition in_any_state_go_to_FAILED = Transition.of(OrderStateTransitionEvent.ANY, FAILED);
		static Transition in_any_state_go_to_PENDING_NEW = Transition.of(OrderStateTransitionEvent.ANY, PENDING_NEW);
		static Transition in_any_state_go_to_NEW = Transition.of(OrderStateTransitionEvent.ANY, NEW);
		static Transition in_any_state_go_to_PENDING_AMEND = Transition.of(OrderStateTransitionEvent.ANY, PENDING_AMEND);
		static Transition in_any_state_go_to_FILLED = Transition.of(OrderStateTransitionEvent.ANY, FILLED);
		static Transition in_any_state_go_to_PENDING_CANCEL = Transition.of(OrderStateTransitionEvent.ANY, PENDING_CANCEL);
		static Transition in_any_state_go_to_CANCELLED = Transition.of(OrderStateTransitionEvent.ANY, CANCELLED);
		static Transition in_any_state_go_to_EXPIRED = Transition.of(OrderStateTransitionEvent.ANY, EXPIRED);
		static Transition in_any_state_go_to_REJECTED = Transition.of(OrderStateTransitionEvent.ANY, REJECTED);

		// IDLE - When 
		static Transition in_IDLE_receive_NEW = Transition.of(IDLE, OrderStateTransitionEvent.NEW, NEW);
		static Transition in_IDLE_receive_REJECTED = Transition.of(IDLE, OrderStateTransitionEvent.REJECTED, REJECTED);
		
		// PENDING NEW
		static Transition in_PENDING_NEW_receive_CREATE_THROTTLED = Transition.of(PENDING_NEW, OrderStateTransitionEvent.CREATE_THROTTLED, THROTTLED);
		static Transition in_PENDING_NEW_receive_TIMEOUT = Transition.of(PENDING_NEW, OrderStateTransitionEvent.TIMEOUT, FAILED);
		static Transition in_PENDING_NEW_receive_NEW  = Transition.of(PENDING_NEW, OrderStateTransitionEvent.NEW, NEW);
		static Transition in_PENDING_NEW_receive_FILLED  = Transition.of(PENDING_NEW, OrderStateTransitionEvent.FILLED, FILLED);	
		static Transition in_PENDING_NEW_receive_REJECTED  = Transition.of(PENDING_NEW, OrderStateTransitionEvent.REJECTED, FAILED);
		static Transition in_PENDING_NEW_receive_CANCEL  = Transition.of(PENDING_NEW, OrderStateTransitionEvent.CANCEL, PENDING_CANCEL);

		// NEW 
		static Transition in_NEW_receive_AMEND = Transition.of(NEW, OrderStateTransitionEvent.AMEND, PENDING_AMEND);
		static Transition in_NEW_receive_CANCEL = Transition.of(NEW, OrderStateTransitionEvent.CANCEL, PENDING_CANCEL);
		static Transition in_NEW_receive_FILLED = Transition.of(NEW, OrderStateTransitionEvent.FILLED, FILLED);		
		
		// PENDING_AMEND
		static Transition in_PENDING_AMEND_receive_AMEND_THROTTLED = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.AMEND_THROTTLED, THROTTLED);
		static Transition in_PENDING_AMEND_receive_TIMEOUT = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.TIMEOUT, NEW);
		static Transition in_PENDING_AMEND_receive_FILLED = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.FILLED, FILLED);	
		static Transition in_PENDING_AMEND_receive_AMENDED = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.AMENDED, NEW);	
		static Transition in_PENDING_AMEND_receive_NEW = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.NEW, NEW);
		static Transition in_PENDING_AMEND_receive_REJECTED = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.REJECTED, REJECTED);
		static Transition in_PENDING_AMEND_goto_NEW = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.REJECTED, NEW);
		static Transition in_PENDING_AMEND_goto_PENDING_NEW = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.REJECTED, NEW);
		static Transition in_PENDING_AMEND_goto_PENDING_CANCEL = Transition.of(PENDING_AMEND, OrderStateTransitionEvent.REJECTED, PENDING_CANCEL);	

		// FILLED - all transitions (if any) driven by exchange order status
		static Transition in_FILLED_receive_TRADE_CANCEL = Transition.of(FILLED, OrderStateTransitionEvent.TRADE_CANCEL, FILLED);
			
		// PENDING_CANCEL
		static Transition in_PENDING_CANCEL_receive_CANCEL_THROTTLED = Transition.of(PENDING_CANCEL, OrderStateTransitionEvent.CANCEL_THROTTLED, THROTTLED);
		static Transition in_PENDING_CANCEL_receive_TIMEOUT = Transition.of(PENDING_CANCEL, OrderStateTransitionEvent.TIMEOUT, NEW);
		static Transition in_PENDING_CANCEL_receive_CANCELLED = Transition.of(PENDING_CANCEL, OrderStateTransitionEvent.CANCELLED, CANCELLED);
		static Transition in_PENDING_CANCEL_receive_REJECTED = Transition.of(PENDING_CANCEL, OrderStateTransitionEvent.REJECTED, REJECTED);
		static Transition in_PENDING_CANCEL_goto_NEW = Transition.of(PENDING_CANCEL, OrderStateTransitionEvent.REJECTED, NEW);
		static Transition in_PENDING_CANCEL_goto_PENDING_NEW = Transition.of(PENDING_CANCEL, OrderStateTransitionEvent.REJECTED, PENDING_NEW);	
		static Transition in_PENDING_CANCEL_goto_PENDING_AMEND = Transition.of(PENDING_CANCEL, OrderStateTransitionEvent.REJECTED, PENDING_AMEND);	

		// CANCELLED - all transitions (if any) driven by exchange order status
		static Transition in_CANCELLED_receive_TRADE_CANCEL = Transition.of(CANCELLED, OrderStateTransitionEvent.TRADE_CANCEL, CANCELLED);
		
		// EXPIRED - all transitions (if any) driven by exchange order status
		
		// REJECTED - all transitions (if any) driven by exchange order status
		
		// FAILED - all transitions (if any) driven by exchange order status
		// FAILED -> NEW is possible if a NEW is received from the exchange after we've triggered timeout of NEW

		// THROTTLED
		static Transition in_THROTTLED_receive_CREATE = Transition.of(THROTTLED, OrderStateTransitionEvent.CREATE, PENDING_NEW);
		static Transition in_THROTTLED_receive_AMEND = Transition.of(THROTTLED, OrderStateTransitionEvent.AMEND, PENDING_AMEND);
		static Transition in_THROTTLED_receive_CANCEL = Transition.of(THROTTLED, OrderStateTransitionEvent.CANCEL, PENDING_CANCEL);
	}

}
