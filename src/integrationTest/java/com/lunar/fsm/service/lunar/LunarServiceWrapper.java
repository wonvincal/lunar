package com.lunar.fsm.service.lunar;

import com.lunar.service.AdminService;
import com.lunar.service.AdminServiceWrapper;
import com.lunar.service.ServiceLifecycleAware;

public class LunarServiceWrapper {
    private final LunarService service;
    
    public static LunarServiceWrapper of(LunarService service){
        return new LunarServiceWrapper(service);
    }
    
    LunarServiceWrapper(LunarService service){
        this.service = service;
    }
    
    public ServiceLifecycleAware coreService(){
        return service.service();
    }
    
    public AdminServiceWrapper adminService(){
        return AdminServiceWrapper.of((AdminService)service.service());
    }

    public State state(){
        return service.state();
    }
    
    public boolean warmupCompleted(){
        return service.warmupCompleted();
    }
}
