package com.lunar.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.logging.log4j.util.Strings;

import com.lunar.entity.EntityLoaderType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TemplateType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

public class PersistServiceConfig extends ServiceConfig {
    private static String ENTITY_TYPE_PATH = "entityType";
    private static String DATABASE_URL_KEY = "dbUrl";

    private final String dbUrl;

    private final EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings;

    public PersistServiceConfig(int systemId, String service, Config config, EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings) {
        super(systemId, service, config);
        this.entityTypeSettings = entityTypeSettings;
        this.dbUrl = config.hasPath(DATABASE_URL_KEY) ? config.getString(DATABASE_URL_KEY) : null;
    }

    public PersistServiceConfig(int systemId, String service, String name, String desc, 
            ServiceType serviceType,
            Optional<String> serviceClass,
            int sinkId,
            Optional<Integer> queueSize,
            Optional<Integer> entityInitialCapacity,
            Optional<Integer> requiredNumThread,
            Optional<Integer> boundToCpu,
            String dbUrl,
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
        this.dbUrl = dbUrl;
    }

    public String dbUrl() {
        return dbUrl;
    }

    public EnumMap<TemplateType, EntityTypeSetting> entityTypeSettings(){
        return entityTypeSettings;
    }

    public static class EntityTypeSetting {
        private static final String LOADER_TYPE_KEY = "loaderType";
        private static final String URI_KEY = "uri";
        private static String NUM_ENTITIES_KEY = "numEntities";

        private final String uri;
        private final EntityLoaderType loaderType;
        private final int numEntities;

        public EntityTypeSetting(EntityLoaderType loaderType, String uri, int numEntities){
            this.loaderType = loaderType;
            this.uri = uri;
            this.numEntities = numEntities;
        }
        public EntityLoaderType loaderType(){
            return loaderType;
        }
        public String uri(){
            return uri;
        }
        public int numEntities(){
            return numEntities;
        }
        public static EntityTypeSetting of(EntityLoaderType loaderType, String uri, int numEntities){
            return new EntityTypeSetting(loaderType, uri, numEntities);
        }
        public static EntityTypeSetting of(Config config){
            return EntityTypeSetting.of(EntityLoaderType.valueOf(config.getString(LOADER_TYPE_KEY)),
                    config.getString(URI_KEY),
                    config.getInt(NUM_ENTITIES_KEY));
        }
        public static final EntityTypeSetting NULL_SETTING = new EntityTypeSetting(EntityLoaderType.NULL, Strings.EMPTY, 0);
    }

    public static PersistServiceConfig of(int systemId, String service, Config config){
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
        return new PersistServiceConfig(systemId, service, config, entityTypeSettings);
    }

}
