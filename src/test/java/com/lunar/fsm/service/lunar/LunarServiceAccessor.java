package com.lunar.fsm.service.lunar;

/**
 * To circumvent the package based visibility of {@link LunarService}
 * @author Calvin
 *
 */
public class LunarServiceAccessor {
	private LunarService lunarService;
	private LunarServiceAccessor(LunarService lunarService){
		this.lunarService = lunarService;
	}
	public static LunarServiceAccessor of(LunarService lunarService){
		return new LunarServiceAccessor(lunarService);
	}
	public LunarServiceAccessor state(State newState){
		lunarService.state(newState);
		return this;
	}
	public State state(){
		return lunarService.state();
	}
}
