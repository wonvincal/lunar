package com.lunar.order;

/**
 * Handler for order state updates
 * @author Calvin
 *
 */
public interface OrderStateHandler {
	void onPendingNew(Order order);
	void onNew(Order order);
	void onUpdated(Order order);	
	void onRejected(Order order);
	void onCancelled(Order order);
	void onFailed(Order order);
	void onFilled(Order order);
	void onExpired(Order order);
	
	public static OrderStateHandler NULL_HANDLER = new OrderStateHandler() {
		@Override
		public void onPendingNew(Order order) {}

		@Override
		public void onNew(Order order) {}

		@Override
		public void onUpdated(Order order) {}

		@Override
		public void onRejected(Order order) {}

		@Override
		public void onCancelled(Order order) {}

		@Override
		public void onFailed(Order order) {}

		@Override
		public void onFilled(Order order) {}

		@Override
		public void onExpired(Order order) {}
	};
}
