package com.lunar.service;

import com.lunar.config.ServiceConfig;
import com.lunar.fsm.service.lunar.LunarService;

public interface ServiceBuilder {
	ServiceLifecycleAware build(ServiceConfig serviceConfig, LunarService messageService);
}
