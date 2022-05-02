package com.lunar.fsm.service.lunar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface LunarServiceStateHook {
    public static final Logger LOG = LogManager.getLogger(LunarServiceStateHook.class);
    default public void onActiveEnter(int sinkId, String name){}
    public void onResetExit(int sinkId, String name);
    
    public static LunarServiceStateHook NULL_HOOK = new LunarServiceStateHook() {
        @Override
        public void onResetExit(int sinkId, String name) {
        }
        @Override
        public void onActiveEnter(int sinkId, String name) {
            // TODO Auto-generated method stub
            
        }
    };
    
    public static LunarServiceStateHook LOG_HOOK = new LunarServiceStateHook() {
        
        @Override
        public void onResetExit(int sinkId, String name) {
        }
        
        @Override
        public void onActiveEnter(int sinkId, String name) {
            LOG.info("Service enters ACTIVE state [sinkId:{}, name:{}]", sinkId, name);
        }
    };
}
