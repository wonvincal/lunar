package com.lunar.service;

import com.lunar.fsm.service.lunar.LunarServiceWrapper;
import com.lunar.order.OrderManagementAndExecutionServiceWrapper;

public class AdminServiceWrapper {
    private final AdminService adminService;

    public static AdminServiceWrapper of(AdminService adminService){
        return new AdminServiceWrapper(adminService);
    }

    AdminServiceWrapper(AdminService adminService){
        this.adminService = adminService;
    }
    
    public LunarServiceWrapper childService(int sinkId){
        MessageServiceExecutionContext messageServiceExecutionContext = this.adminService.children().get(sinkId);
        if (messageServiceExecutionContext == null){
            throw new IllegalArgumentException("Cannot find lunar service with sinkId [sinkId:" + sinkId + "]");
        }
        return LunarServiceWrapper.of(messageServiceExecutionContext.messageService());
    }
    
    public OrderManagementAndExecutionServiceWrapper childAsOMES(int sinkId){
        return OrderManagementAndExecutionServiceWrapper.of(this.childService(sinkId));
    }
    
    public void stopDashboard(){
    	adminService.stopDashboard();
    }
    
    public void startDashboard(){
    	adminService.startDashboard();
    }
}
