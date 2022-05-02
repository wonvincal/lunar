package com.lunar.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map.Entry;
import java.util.Optional;

import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

public class ReferenceDataServiceConfig extends ServiceConfig {
	private static String ENTITY_TYPE_PATH = "entityType";
	
	EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings = new EnumMap<TemplateType, EntityTypeSetting>(TemplateType.class);
	
	public ReferenceDataServiceConfig(int systemId, String service, Config config, EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings) {
		super(systemId, service, config);
		this.entityTypeSettings = entityTypeSettings;
	}
//			 config.hasPath(DATABASE_URL_KEY) ? Optional.of(config.getString(DATABASE_URL_KEY)) : Optional.empty(),

	public ReferenceDataServiceConfig(int systemId, String service, String name, String desc, 
			ServiceType serviceType,
			Optional<String> serviceClass,
			int sinkId,
			Optional<Integer> queueSize,
			Optional<Integer> entityInitialCapacity,
			Optional<Integer> requiredNumThread,
			Optional<Integer> boundToCpu,
			boolean create,
			boolean warmup,
			Duration stopTimeout,
			boolean journal,
			String journalFileName,
			EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings){
		super(systemId, service, name, desc, serviceType, serviceClass, sinkId,
				queueSize,
				entityInitialCapacity,
				requiredNumThread,
				boundToCpu,
				create,
				warmup,
				stopTimeout,
				journal,
				journalFileName);
		this.entityTypeSettings = entityTypeSettings;
	}
	
	public EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings(){
		return entityTypeSettings;
	}
	
	public static class EntityTypeSetting {
	    private static String NUM_ENTITIES_KEY = "numEntities";
	    
	    private final int numEntities;

	    public EntityTypeSetting(int numEntities){
			this.numEntities = numEntities;
		}
		public int numEntities() {
		    return numEntities;
		}
		public static EntityTypeSetting of(int numEntities){
			return new EntityTypeSetting(numEntities);
		}
        public static EntityTypeSetting of(Config config){
            return new EntityTypeSetting(config.getInt(NUM_ENTITIES_KEY));
        }		
		public static final EntityTypeSetting NULL_SETTING = new EntityTypeSetting(0);
	}
	
	public static ReferenceDataServiceConfig of(int systemId, String service, Config config){
		EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings = new EnumMap<TemplateType, EntityTypeSetting>(TemplateType.class);
		
		if (config.hasPath(ENTITY_TYPE_PATH)){
			for (Entry<String, ConfigValue> entry : config.getObject(ENTITY_TYPE_PATH).entrySet()){
				String key = entry.getKey(); // entity type
				TemplateType templateType = TemplateType.valueOf(key);
				entityTypeSettings.put(templateType,
						EntityTypeSetting.of(config.getConfig(ENTITY_TYPE_PATH + "." + key)));
			}
		}
		for (TemplateType templateType : TemplateType.values()){
			if (!entityTypeSettings.containsKey(templateType)){
				entityTypeSettings.put(templateType, EntityTypeSetting.NULL_SETTING);
			}
		}
		return new ReferenceDataServiceConfig(systemId, service, config, entityTypeSettings);
	}

}
