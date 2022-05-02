package performance.baseline3;



public class StateTransitionEventAccessor {
	private BaseStateService service;
	public StateTransitionEventAccessor(BaseStateService service){
		this.service = service;
	}
	public static StateTransitionEventAccessor of(BaseStateService service){
		return new StateTransitionEventAccessor(service);
	}
	public StateTransitionEvent event(){
		return service.stateEvent();
	}
}
