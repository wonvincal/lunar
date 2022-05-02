package com.lunar.fsm.service.lunar;

public class RemoteStateHook implements LunarServiceStateHook {
    LunarServiceStateHook hook = LunarServiceStateHook.NULL_HOOK;
    
    public RemoteStateHook(){}
    
    @Override
    public void onResetExit(int sinkId, String name) {
        hook.onResetExit(sinkId, name);
    }
    
    @Override
    public void onActiveEnter(int sinkId, String name) {
    	hook.onActiveEnter(sinkId, name);
    }
    
    public RemoteStateHook hook(LunarServiceStateHook hook){
        this.hook = hook;
        return this;
    }
    
    public void clear(){
    	this.hook = LunarServiceStateHook.NULL_HOOK;
    }
}