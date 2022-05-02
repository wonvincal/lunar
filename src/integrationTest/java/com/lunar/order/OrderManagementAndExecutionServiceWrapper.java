package com.lunar.order;

import com.lunar.fsm.service.lunar.LunarServiceWrapper;
import com.lunar.fsm.service.lunar.State;

public class OrderManagementAndExecutionServiceWrapper {
    private final OrderManagementAndExecutionService omesService;
    private final LunarServiceWrapper lunarService;
    
    public static OrderManagementAndExecutionServiceWrapper of(LunarServiceWrapper lunarService){
        
        return new OrderManagementAndExecutionServiceWrapper((OrderManagementAndExecutionService)lunarService.coreService(), lunarService);
    }

    public static OrderManagementAndExecutionServiceWrapper of(OrderManagementAndExecutionService omesService, LunarServiceWrapper lunarService){
        return new OrderManagementAndExecutionServiceWrapper(omesService, lunarService);
    }

    OrderManagementAndExecutionServiceWrapper(OrderManagementAndExecutionService omesService, LunarServiceWrapper lunarService){
        this.omesService = omesService;
        this.lunarService = lunarService;
    }
    
    public LunarServiceWrapper lunarService(){
        return lunarService;
    }
    
    public State state(){
        return lunarService.state();
    }
    
    public boolean isClear(){
        return omesService.isClear();
    }
}
