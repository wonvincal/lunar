package com.lunar.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lunar.message.io.sbe.ServiceType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Holds info on parsed configuration file.
 * Important - all services should be child of the main admin actor.  Exception is thrown if admin actor is not found in the 
 * configuration file.
 *  
 * @author Calvin
 *
 */
public class SystemConfig {
	private static final Logger LOG = LogManager.getLogger(SystemConfig.class);
	private static final String SERVICE_PATH = "lunar.service";
	private static final String SERVICE_INSTANCES_PATH = "lunar.numServiceInstances";
	private static final String LOGGING_JOURNAL_FOLDER_PATH = "lunar.logging.journalFolder";
	private static final String LOGGING_JOURNAL_ARCHIVE_FOLDER_PATH = "lunar.logging.journalArchiveFolder";
	private static final String LOGGING_ARCHIVE_ON_START_PATH = "lunar.logging.archiveOnStart";
	private static final String LOGGING_ARCHIVE_FOLDER_PATH = "lunar.logging.archiveFolder";
	private static final String LOGGING_FOLDER_PATH = "lunar.logging.folder";
	private static final String SYSTEM_LIST_KEY = "system";
	private static final String SYSTEM_ID_KEY = "lunar.system.id";
	private static final String ADMIN_SINK_ID_KEY = "lunar.service.admin.sinkId";
	
	private int systemId;
	private final MessagingConfig msgConfig;
	private final TimerServiceConfig tsConfig;
	private final CommunicationConfig communicationConfig;
	private final Int2ObjectArrayMap<CommunicationConfig> remoteCommunicationConfigs;
	private final AdminServiceConfig adminServiceConfig;
	private final List<ServiceConfig> serviceConfigs;
	private final String name;
	private final String journalFolder;
	private final String journalArchiveFolder;
	private final int numServiceInstances;
	private final boolean loggingArchiveOnStart;
	private final String loggingArchiveFolder;
	private final String loggingFolder;
	private final String host;
	private final int port;
	private boolean isJournalRequired = false; 
	
	public static SystemConfig of(String name, String host, int port, Config wholeConfig){
		List<ServiceConfig> newServiceConfigs = new ArrayList<ServiceConfig>();
		Int2ObjectArrayMap<CommunicationConfig> newRemoteCommunicationConfigs;
		MessagingConfig msgConfig;
		TimerServiceConfig tsConfig;
		CommunicationConfig communicationConfig = null;
		int systemId;
		int numServiceInstances;
		String journalFolder;
		String journalArchiveFolder;
		AdminServiceConfig adminServiceConfig = null;
		final AtomicReference<AdminServiceConfig> adminServiceConfigReference= new AtomicReference<AdminServiceConfig>();
		String service = "";
		boolean loggingArchiveOnStart;
		String loggingArchiveFolder;
		String loggingFolder;

		final AtomicBoolean isJournalRequired = new AtomicBoolean(false);
		try {
			final IntOpenHashSet sinkIds = new IntOpenHashSet();
			final ObjectOpenHashSet<String> journalFileNameSet = new ObjectOpenHashSet<>();
			List<String> systemList = wholeConfig.getStringList(SYSTEM_LIST_KEY);
			newRemoteCommunicationConfigs = new Int2ObjectArrayMap<>(systemList.size());
			for (String systemName : systemList){
				Config config = wholeConfig.getConfig(systemName).withFallback(wholeConfig);
				systemId = config.getInt(SYSTEM_ID_KEY);
				int adminSinkId = config.getInt(ADMIN_SINK_ID_KEY);
				if (sinkIds.contains(adminSinkId)){
					throw new IllegalArgumentException("Duplicate sink ID is found [sinkId:" + adminSinkId + "]");
				}
				sinkIds.add(adminSinkId);
				if (systemName.equalsIgnoreCase(name)){
					communicationConfig = CommunicationConfig.of(systemId, adminSinkId, config);
				}
				else{
					newRemoteCommunicationConfigs.put(systemId, CommunicationConfig.of(systemId, adminSinkId, config));
				}
			}
			wholeConfig = wholeConfig.getConfig(name).withFallback(wholeConfig);
			msgConfig = MessagingConfig.of(wholeConfig);
			systemId = wholeConfig.getInt(SYSTEM_ID_KEY);
			numServiceInstances = wholeConfig.getInt(SERVICE_INSTANCES_PATH);
			journalFolder = wholeConfig.getString(LOGGING_JOURNAL_FOLDER_PATH);
			journalArchiveFolder = wholeConfig.getString(LOGGING_JOURNAL_ARCHIVE_FOLDER_PATH);
			for (Entry<String, ConfigValue> entry : wholeConfig.getObject(SERVICE_PATH).entrySet()){
				service = entry.getKey();
				Config specificConfig = ServiceConfig.getSpecificConfig(service, wholeConfig);
				if (!ServiceConfig.shouldCreate(specificConfig)){
					continue;
				}
				ServiceConfig serviceConfig = null;
				ServiceType serviceType = ServiceConfig.getServiceType(specificConfig);
				switch (serviceType){
				case AdminService:
					adminServiceConfig = new AdminServiceConfig(systemId, service, specificConfig);
					adminServiceConfigReference.set(adminServiceConfig);
					serviceConfig = adminServiceConfig;
					break;
				case ClientService:
					serviceConfig = ClientServiceConfig.of(systemId, service, specificConfig);
					break;
				case DashboardService:
					serviceConfig = DashboardServiceConfig.of(systemId, service, specificConfig);
					break;
				case DashboardStandAloneWebService:
					serviceConfig = DashboardStandAloneWebServiceConfig.of(systemId, service, specificConfig);
					break;
				case EchoService:
					break;
				case ExchangeService:
					serviceConfig = ExchangeServiceConfig.of(systemId, service, specificConfig);
					break;
				case MarketDataService:
					serviceConfig = MarketDataServiceConfig.of(systemId, service, specificConfig);
					break;
				case MarketDataSnapshotService:
				    serviceConfig = MarketDataSnapshotServiceConfig.of(systemId, service, specificConfig);
				    break;
				case NotificationService:
					serviceConfig = NotificationServiceConfig.of(systemId, service, specificConfig);
					break;
				case OrderManagementAndExecutionService:
					serviceConfig = OrderManagementAndExecutionServiceConfig.of(systemId, service, specificConfig);
					break;
				case OrderAndTradeSnapshotService:
					serviceConfig = OrderAndTradeSnapshotServiceConfig.of(systemId, service, specificConfig);
					break;
				case PerformanceService:
					serviceConfig = new PerformanceServiceConfig(systemId, service, specificConfig);
					break;
                case PersistService:
                    serviceConfig = PersistServiceConfig.of(systemId, service, specificConfig);
                    break;
                case PingService:
                	serviceConfig = PingServiceConfig.of(systemId, service, specificConfig);
                	break;
                case PongService:
                	serviceConfig = PongServiceConfig.of(systemId, service, specificConfig);
                	break;
				case PortfolioAndRiskService:
					serviceConfig = PortfolioAndRiskServiceConfig.of(port, service, specificConfig);
					break;
				case RefDataService:
					serviceConfig = ReferenceDataServiceConfig.of(systemId, service, specificConfig);
					break;
				case StrategyService:
					serviceConfig = StrategyServiceConfig.of(systemId, service, specificConfig);
					break;
				case WarmupService:
					serviceConfig = WarmupServiceConfig.of(port, service, specificConfig);
					break;
				case PricingService:
				    serviceConfig = PricingServiceConfig.of(port, service, specificConfig);
				    break;
				case ScoreBoardService:
				    serviceConfig = ScoreBoardServiceConfig.of(port, service, specificConfig);
				    break;
				case NULL_VAL:
					throw new IllegalArgumentException("cannot create service config from null val, name: " + name);
				default:
					LOG.warn("no exact match of service config for {}, use default", serviceType);
					serviceConfig = new ServiceConfig(systemId, name, specificConfig);
				}
				if (serviceConfig != null){
					newServiceConfigs.add(serviceConfig);
				}
			}
			if (adminServiceConfig == null){
				throw new IllegalArgumentException("admin service config is either not found in the configuration section for " + name + " or is marked as 'create = false'");
			}
 			newServiceConfigs.stream().forEach(c -> {
				if (c.serviceType() != ServiceType.AdminService){
					if (sinkIds.contains(c.sinkId())){
						throw new IllegalArgumentException("Duplicate sink ID is found [sinkId:" + c.sinkId() + "]");
					}
					sinkIds.add(c.sinkId());
					adminServiceConfigReference.get().addChildServiceConfig(c);
				}
				
				if (c.journal()){
					isJournalRequired.set(true);
					if (!journalFileNameSet.add(c.journalFileName())){
						throw new IllegalArgumentException("Duplicate journal file name is found [sinkId:" + c.sinkId() + "]");
					}
				}
			});
 			newRemoteCommunicationConfigs.forEach(new BiConsumer<Integer, CommunicationConfig>() {

				@Override
				public void accept(Integer t, CommunicationConfig u) {
					adminServiceConfigReference.get().addRemoteAeronConfig(u);
				}
			});
 			adminServiceConfig.localAeronConfig(communicationConfig);
			tsConfig = new TimerServiceConfig(wholeConfig);
			loggingArchiveFolder = wholeConfig.getString(LOGGING_ARCHIVE_FOLDER_PATH);
			loggingArchiveOnStart = wholeConfig.getBoolean(LOGGING_ARCHIVE_ON_START_PATH);
			loggingFolder = wholeConfig.getString(LOGGING_FOLDER_PATH);
		}
		catch (Exception e){
			throw new IllegalArgumentException("caught exception when extracting config | systemName:" + name + ", service:" + service, e);
		}
		return new SystemConfig(name, host, port, msgConfig, tsConfig, numServiceInstances, adminServiceConfig, 
				newServiceConfigs, 
				communicationConfig, 
				newRemoteCommunicationConfigs,
				loggingArchiveOnStart,
				loggingFolder,
				loggingArchiveFolder,
				isJournalRequired.get(),
				journalFolder,
				journalArchiveFolder);
	}
	
	public static SystemConfig of(String name, String host, int port, 
			MessagingConfig msgConfig, 
			TimerServiceConfig tsConfig, 
			int numServiceInstances, 
			AdminServiceConfig adminServiceConfig, 
			List<ServiceConfig> serviceConfigs, 
			CommunicationConfig communicationConfig, 
			Int2ObjectArrayMap<CommunicationConfig> remoteCommunicationConfigs,
			boolean loggingArchiveOnStart,
			String loggingFolder,
			String loggingArchiveFolder,
			boolean isJournalRequired,
			String journalFolder,
			String journalArchiveFolder			
			){
		return new SystemConfig(name, host, port, msgConfig, tsConfig, numServiceInstances, adminServiceConfig, serviceConfigs, communicationConfig, remoteCommunicationConfigs, loggingArchiveOnStart, loggingFolder, loggingArchiveFolder, isJournalRequired, journalFolder, journalArchiveFolder);
	}
	
	SystemConfig(String name, String host, int port, 
			MessagingConfig msgConfig, 
			TimerServiceConfig tsConfig, 
			int numServiceInstances, 
			AdminServiceConfig adminServiceConfig, 
			List<ServiceConfig> serviceConfigs, 
			CommunicationConfig communicationConfig, 
			Int2ObjectArrayMap<CommunicationConfig> remoteCommunicationConfigs,
			boolean loggingArchiveOnStart,
			String loggingFolder,
			String loggingArchiveFolder,
			boolean isJournalRequired,
			String journalFolder,
			String journalArchiveFolder			
			){
		this.name = name;
		this.host = host;
		this.port = port;
		this.adminServiceConfig = adminServiceConfig;
		this.serviceConfigs = serviceConfigs;
		this.remoteCommunicationConfigs = remoteCommunicationConfigs;
		this.msgConfig = msgConfig;
		this.tsConfig = tsConfig;
		this.numServiceInstances = numServiceInstances;
		this.communicationConfig = communicationConfig;
		this.loggingFolder = loggingFolder;
		this.loggingArchiveFolder = loggingArchiveFolder;
		this.loggingArchiveOnStart = loggingArchiveOnStart;
		this.isJournalRequired = isJournalRequired;
		this.journalFolder = journalFolder;
		this.journalArchiveFolder = journalArchiveFolder;
	}
	
	public int systemId(){
		return systemId;
	}

	public String host(){
		return host;
	}
	
	public int port(){
		return port;
	}
	
	public Int2ObjectArrayMap<CommunicationConfig> remoteAeronConfigs(){
		return remoteCommunicationConfigs;
	}
	
	public CommunicationConfig aeronConfig(){
		return communicationConfig;
	}
	
	public AdminServiceConfig adminServiceConfig(){
		return adminServiceConfig;
	}
	
	public List<ServiceConfig> serviceConfigs(){
		return serviceConfigs;
	}
	
	public int numServiceInstances(){
		return numServiceInstances;
	}
	
	public String journalFolder(){
		return this.journalFolder;
	}
	
	public String journalArchiveFolder(){
		return this.journalArchiveFolder;
	}
	
	public String name(){
		return this.name;
	}
	
	public TimerServiceConfig timerServiceConfig(){
		return tsConfig;
	}

	public boolean loggingArchiveOnStart(){
		return loggingArchiveOnStart;
	}
	
	public String loggingArchiveFolder(){
		return loggingArchiveFolder;
	}

	public String loggingFolder(){
		return loggingFolder;
	}

	public MessagingConfig messagingConfig(){
		return msgConfig;
	}
	
	public int deadLettersSinkId(){
		return 0;
	}
	
	public boolean isJournalRequired(){
		return isJournalRequired;
	}
}
