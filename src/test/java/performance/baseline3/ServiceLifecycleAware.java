package performance.baseline3;

import com.lunar.core.CommandTracker;
import com.lunar.core.RequestTracker;
import com.lunar.core.ServiceStatusTracker;

public interface ServiceLifecycleAware {
	// -- IDLE --
	StateTransitionEvent idleStart(BaseStateService baseStateService, ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);
	StateTransitionEvent idleRecover(ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);
	void idleExit();

	// -- WAITING_FOR_SERVICES -- 
	StateTransitionEvent waitingForServicesEnter(ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);
	void waitingForServicesExit();
	
	// -- ACTIVE --
	StateTransitionEvent activeEnter(ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);
	void activeExit(ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);

	// -- STOP --
	StateTransitionEvent stopEnter(ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);
	void stopExit();

	// -- STOPPED --
	StateTransitionEvent stoppedEnter(ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);
	void stoppedExit(ServiceStatusTracker serviceTracker, RequestTracker requestTracker, CommandTracker commandTracker);

}
