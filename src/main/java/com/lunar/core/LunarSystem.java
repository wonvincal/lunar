package com.lunar.core;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.SigIntBarrier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.config.AdminServiceConfig;
import com.lunar.config.SystemConfig;
import com.lunar.exception.ConfigurationException;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.fsm.service.lunar.LunarServiceStateHook;
import com.lunar.marketdata.MarketDataReplayer;
import com.lunar.message.MessageFactory;
import com.lunar.message.Parameter;
import com.lunar.message.Parameters;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.ParameterType;
import com.lunar.message.io.sbe.RequestType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.io.sbe.TimerEventSbeEncoder;
import com.lunar.message.io.sbe.TimerEventType;
import com.lunar.message.sender.TimerEventSender;
import com.lunar.message.sink.MessageSink;
import com.lunar.message.sink.MessageSinkRef;
import com.lunar.message.sink.MessageSinkRefMgr;
import com.lunar.message.sink.NormalMessageSinkRefMgr;
import com.lunar.message.sink.ValidNullMessageSink;
import com.lunar.service.AdminService;
import com.lunar.service.HashedWheelTimerService;
import com.lunar.service.ServiceConstant;
import com.lunar.service.ServiceFactory;
import com.lunar.util.PathUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * A replacement of actor system
 * @author Calvin
 *
 */
public class LunarSystem implements AutoCloseable, LunarSystemMBean {
	public enum LunarSystemState{
		IDLE,
		START_IN_PROGRESS,
		STARTED,
		STOP_IN_PROGRESS,
		STOPPED
	};
	
	private static final Logger LOG = LogManager.getLogger(LunarSystem.class);
	private static final int LUNAR_SERVICE_SHUTDOWN_POLL_FREQ_IN_MS = 500;
	public static final String LUNAR_SYSTEM_PROP = "lunar.system";
	public static final String LUNAR_AERON_DIR_PROP = "lunar.aeronDir";
	public static final String LUNAR_PORT_PROP = "lunar.port";
	public static final String LUNAR_HOST_PROP = "lunar.host";
	public static final String LUNAR_JMX_PORT_PROP = "lunar.jmxPort";
	public static final String CONFIG_FILE_PROP = "config.file";
	public static final String REPLAY_DATE_PROP = "replay.date";
	public static final String REPLAY_SPEED_PROP = "replay.speed";
	public static final String REPLAY_PREOPEN_START_TIME_HHMI_PROP = "replay.preopen.start.time.hhmi";
	public static final String SPEEDARB_VERSION = "speedarb.version";
	public static final String LUNAR_SYSTEM_STARTUP_TIMEOUT_IN_SEC = "lunar.system.startupTimeoutInSec";
	public static final int DEFAULT_LUNAR_SYSTEM_STARTUP_TIMEOUT_IN_SEC = 180;

	private TimerService timerService;
	private LunarService adminService;
	private MessageSinkRefMgr messageSinkRefMgr;
	private AtomicReference<LunarSystemState> currentState;
	private final CompletableFuture<Boolean> readyFuture;
	private final LunarServiceStateHook stateHook;

	public static void main(final String[] args) throws Exception
	{
		// get systemName from property
		String systemName = System.getProperty(LUNAR_SYSTEM_PROP);
		if (Strings.isNullOrEmpty(systemName)){
			LOG.error("property " + LUNAR_SYSTEM_PROP + " is undefined, exiting...");
			return;
		}		
		String portStr = System.getProperty(LUNAR_PORT_PROP);
		if (Strings.isNullOrEmpty(portStr)){
			LOG.error("property {} is undefined, exiting...", LUNAR_PORT_PROP);
			return;
		}
		Integer port = Ints.tryParse(portStr);
		if (port == null){
			LOG.error("property {} is not a valid integer ({}), exiting...", LUNAR_PORT_PROP, portStr);
			return;
		}
		String host = System.getProperty(LUNAR_HOST_PROP);
		if (Strings.isNullOrEmpty(host)){
			LOG.error("property {} is undefined, exiting...", LUNAR_HOST_PROP);
			return;
		}
		String jmxPortStr = System.getProperty(LUNAR_JMX_PORT_PROP);
		if (Strings.isNullOrEmpty(portStr)){
			LOG.error("property {} is undefined, exiting...", LUNAR_JMX_PORT_PROP);
			return;
		}
		Integer jmxPort = Ints.tryParse(jmxPortStr);
		if (jmxPort == null){
			LOG.error("property {} is not a valid integer ({}), exiting...", LUNAR_JMX_PORT_PROP, portStr);
			return;
		}
		int startupTimeout = DEFAULT_LUNAR_SYSTEM_STARTUP_TIMEOUT_IN_SEC;
		String systemStartupTimeout = System.getProperty(LUNAR_SYSTEM_STARTUP_TIMEOUT_IN_SEC);
		if (!Strings.isNullOrEmpty(systemStartupTimeout)){
			Integer parsedStartupTimeout = Ints.tryParse(systemStartupTimeout);
			if (parsedStartupTimeout != null){
				startupTimeout = parsedStartupTimeout;
			}
			else {
				LOG.warn("property {} is available, but is not a valid integer {}, use default {}", LUNAR_SYSTEM_STARTUP_TIMEOUT_IN_SEC, systemStartupTimeout, DEFAULT_LUNAR_SYSTEM_STARTUP_TIMEOUT_IN_SEC);		        
			}
		}
		LOG.info("Startup timeout [sec:{}]", startupTimeout);

		final SystemClock systemClock;
		final boolean replayMode;
		final int replaySpeed;
		final String replayDateStr = System.getProperty(REPLAY_DATE_PROP);
		Optional<Long> preOpenStartTimeNs = Optional.empty();
		if (Strings.isNullOrEmpty(replayDateStr)) {
			systemClock = new RealSystemClock();
			replayMode = false;
			replaySpeed = 0;
		}
		else {
			int replayDate = Integer.parseInt(replayDateStr);
			int date = replayDate % 100;
			replayDate = (replayDate - date) / 100;
			int month = replayDate % 100;
			int year = (replayDate - month) / 100;
			systemClock = new UserControlledSystemClock(LocalDate.of(year, month, date));
			replayMode = true;
			
			final String replaySpeedStr = System.getProperty(REPLAY_SPEED_PROP);
			if (Strings.isNullOrEmpty(replaySpeedStr)) {
			    replaySpeed = 1;
			}
			else {
			    replaySpeed = Integer.parseInt(replaySpeedStr);
			}			
			
			final String replayStartTimeStr = System.getProperty(REPLAY_PREOPEN_START_TIME_HHMI_PROP);
			if (!Strings.isNullOrEmpty(replayStartTimeStr)) {
				try {
					if (replayStartTimeStr.length() == 4){
						int hour = Integer.parseInt(replayStartTimeStr.substring(0, 2));
						int minute = Integer.parseInt(replayStartTimeStr.substring(2, 4));
						preOpenStartTimeNs = Optional.of(LocalTime.of(hour, minute).toNanoOfDay());
					}
				}
				catch (Exception e){
					LOG.error("Invalid replay start time [prop:" + REPLAY_PREOPEN_START_TIME_HHMI_PROP + ", value:" + replayStartTimeStr, e);
				}
			} 
		}
		CompletableFuture<Boolean> readyFuture = new CompletableFuture<Boolean>();
		try (final LunarSystem lunar = LunarSystem.launch(systemName, host, port, jmxPort, systemClock, readyFuture)){
			readyFuture.get(startupTimeout, TimeUnit.SECONDS);

			Messenger childMessenger = lunar.adminService().messenger().createChildMessenger();
			childMessenger.sendRequest(lunar.getMessageSinkRefMgr().omes(), RequestType.UPDATE, Parameters.listOf(Parameter.of(ParameterType.PURCHASING_POWER, 10_000_000_000l)));

			if (replayMode) {
				LOG.info("Start replayer [date:{}, replaySpeed:{}], preOpenStartTime:{}]", systemClock.dateString(), replaySpeed, preOpenStartTimeNs);
				MarketDataReplayer replayer = MarketDataReplayer.instanceOf();
				replayer.startFeed(true, replaySpeed, preOpenStartTimeNs, systemClock, true);
			}

			//LockSupport.parkNanos(15_000_000_000l);
			//command = Command.of(senderSink.sinkId(),
			//        clientKey,
			//        CommandType.SEND_ECHO, 
			//        new ImmutableList.Builder<Parameter>().build());
			//lunar.adminService().messenger().createChildMessenger().sendCommand(lunar.getMessageSinkRefMgr().prc().sinkId(), command);
			new SigIntBarrier().await();
		}
		catch (ConfigurationException ex){
			LOG.error("Caught configuration exception when launching lunar system", ex);
		}
		catch (TimeoutException ex){
			LOG.error("Caught timeout exception when launching / closing lunar system", ex);		    
		}
		catch (Exception ex){
			LOG.error("Caught exception when launching / closing lunar system", ex);
		}
		LOG.info("System exited");
	}

	public static LunarSystem launch(String systemName, String host, int port, int jmxPort) throws ConfigurationException{
		return launch(systemName, host, port, jmxPort, new RealSystemClock());
	}

	public static LunarSystem launch(String systemName, String host, int port, int jmxPort, SystemClock systemClock) throws ConfigurationException{
		LunarSystem lunar = new LunarSystem(systemName, host, port, Optional.of(jmxPort), systemClock, new CompletableFuture<Boolean>(), LunarServiceStateHook.LOG_HOOK);
		lunar.start();
		return lunar;
	}

	public static LunarSystem launch(String systemName, String host, int port, SystemClock systemClock, CompletableFuture<Boolean> readyFuture) throws ConfigurationException{
		LunarSystem lunar = new LunarSystem(systemName, host, port, Optional.empty(), systemClock, readyFuture, LunarServiceStateHook.LOG_HOOK);
		lunar.start();
		return lunar;
	}

	public static LunarSystem launch(String systemName, String host, int port, int jmxPort, SystemClock systemClock, CompletableFuture<Boolean> readyFuture) throws ConfigurationException{
		LunarSystem lunar = new LunarSystem(systemName, host, port, Optional.of(jmxPort), systemClock, readyFuture, LunarServiceStateHook.LOG_HOOK);
		lunar.start();
		return lunar;
	}

    public static LunarSystem launch(String systemName, String host, int port, int jmxPort, SystemClock systemClock, CompletableFuture<Boolean> readyFuture, LunarServiceStateHook stateHook) throws ConfigurationException{
        LunarSystem lunar = new LunarSystem(systemName, host, port, Optional.of(jmxPort), systemClock, readyFuture, stateHook);
        lunar.start();
        return lunar;
    }

    private ExecutorService executor;
	private Disruptor<MutableDirectBuffer> disruptor;
	private String systemName;
	private String host;
	private int port;
	private Optional<Integer> jmxPort;
	private SystemClock systemClock;
	
	private LunarSystem(String systemName, String host, int port, Optional<Integer> jmxPort, SystemClock systemClock, CompletableFuture<Boolean> readyFuture, LunarServiceStateHook stateHook){
		this.systemName = systemName;
		this.host = host;
		this.port = port;
		this.jmxPort = jmxPort;
		this.systemClock = systemClock;
		this.currentState = new AtomicReference<LunarSystemState>(LunarSystemState.IDLE);
		this.readyFuture = readyFuture;
		this.stateHook = stateHook;
	}

	@SuppressWarnings("unchecked")
	private void start() throws ConfigurationException{
		String configFile = System.getProperty(CONFIG_FILE_PROP, "application.conf (akka default)");
		LOG.info("starting {} {}:{}, config at {}", systemName, host, port, configFile);

		if (!this.currentState.compareAndSet(LunarSystemState.IDLE, LunarSystemState.START_IN_PROGRESS)){
			throw new IllegalStateException("cannot start a process that is not in idle state");
		}

		ConfigFactory.invalidateCaches();
		final Config rawConfig = ConfigFactory.load().resolve();
		final Config config = rawConfig.getConfig(systemName).withFallback(rawConfig);
		final SystemConfig systemConfig = SystemConfig.of(systemName, host, port, config);
		
		try {
			if (this.jmxPort.isPresent()){
				setupJMXAgent(this.jmxPort.get());
			}
			else {
				LOG.info("No jmx agent will be started");
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Cannot start JMX Agent!", e);
		}
		catch (Exception e){
			throw new RuntimeException("Cannot start MBean!", e);
		}
		
		if (systemConfig.loggingArchiveOnStart()){
			archiveLogs(systemConfig.loggingFolder(), systemConfig.loggingArchiveFolder());
		}
		
		if (systemConfig.isJournalRequired()){
			// Make sure journal folder exists, if not exist, where should I create the folder
			try {
				PathUtil.createWritableFolderIfNotExist("journal", systemConfig.journalFolder());
				PathUtil.createWritableFolderIfNotExist("journal archive", systemConfig.journalArchiveFolder());
			}
			catch (Exception e){
				throw new ConfigurationException("Caught exception setting up journal folder", e);
			}
		}
		
		final MessageFactory messageFactory = new MessageFactory(systemConfig.messagingConfig());
		
		final String replayDateStr = System.getProperty(REPLAY_DATE_PROP);
		timerService = new HashedWheelTimerService(new NamedThreadFactory(systemName, systemName + "timer"), 
				systemConfig.timerServiceConfig().tickDuration(),
				systemConfig.timerServiceConfig().ticksPerWheel(),
				messageFactory.createStandaloneTimerEventSender());
//		if (Strings.isNullOrEmpty(replayDateStr)) {
//			timerService = new HashedWheelTimerService(new NamedThreadFactory(systemName, systemName + "timer"), 
//					systemConfig.timerServiceConfig().tickDuration(),
//					systemConfig.timerServiceConfig().ticksPerWheel(),
//					messageFactory.createStandaloneTimerEventSender());
//		}
//		else {
//			int replayDate = Integer.parseInt(replayDateStr);
//			int date = replayDate % 100;
//			replayDate = (replayDate - date) / 100;
//			int month = replayDate % 100;
//			int year = (replayDate - month) / 100;
//			timerService = new UserControlledTimerService(0, LocalDateTime.of(year, month, date, 0, 0), messageFactory.createStandaloneTimerEventSender());
//		}
		timerService.start();
		
		// Create MessageSinkRefMgr for LunarSystem.  This will be shared
		// by all services in the system
		messageSinkRefMgr = new NormalMessageSinkRefMgr(systemConfig.systemId(),
				systemConfig.numServiceInstances(),
				timerService);
		messageSinkRefMgr.register(
				MessageSinkRef.of(ValidNullMessageSink.of(systemConfig.systemId(), ServiceConstant.DEAD_LETTERS_SINK_ID, ServiceType.DeadLetterService), "dead-letters"));
		
		// create a new executor service to service admin
		AdminServiceConfig adminConfig = systemConfig.adminServiceConfig(); 
		executor = Executors.newSingleThreadExecutor(new NamedThreadFactory(adminConfig.name(), 
				adminConfig.name() + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
		disruptor = new Disruptor<MutableDirectBuffer>(messageFactory.eventFactory(), 
				adminConfig.queueSize().orElseThrow(IllegalArgumentException::new), 
				executor);
		
		// Create service factory and create admin service
		ServiceFactory serviceFactory = ServiceFactory.of(messageSinkRefMgr, messageFactory, timerService, systemClock);
		adminService = serviceFactory.createAdminService(systemConfig, adminConfig, disruptor.getRingBuffer(), readyFuture, stateHook);
		
		
		// Link up adminService with disruptor
		disruptor.handleExceptionsWith(new AdminServiceDisruptorExceptionHandler(adminConfig.name()));
		disruptor.handleEventsWith(adminService);

		disruptor.start();

		if (!currentState.compareAndSet(LunarSystemState.START_IN_PROGRESS, LunarSystemState.STARTED)){
			throw new IllegalStateException("cannot mark a process as started,  current state is " + currentState.get().name());
		}
	}
	
	public LunarService adminService(){
		return adminService;
	}

	/**
	 * Archive all logs including aeron log buffers to another folder
	 * TODO Implementation
	 * @param folder
	 * @param archiveFolder
	 */
	private void archiveLogs(String folder, String archiveFolder){
		LOG.info("archive logs from {} to {}", folder, archiveFolder);
	}
	
	private void setupJMXAgent(int jmxPort) throws IOException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException {
		// Ensure cryptographically strong random number generator used
		// to choose the object number - see java.rmi.server.ObjID
		//
		System.setProperty("java.rmi.server.randomIDs", "true");

		// Start an RMI registry on port 8787
		//
		LocateRegistry.createRegistry(jmxPort);

		// Retrieve the PlatformMBeanServer.
		//
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();

		ObjectName name = new ObjectName("com.lunar.jmx:type=LunarSystem");
		mbs.registerMBean(this, name);
		LOG.info("Setup MBean for LunarSystem successfully");
		
		// Environment map.
		//
		HashMap<String, Object> env = new HashMap<String, Object>();

		// This where we would enable security - left out of this
		// for the sake of the example....
		//

		// Create an RMI connector server.
		//
		// As specified in the JMXServiceURL the RMIServer stub will be
		// registered in the RMI registry running in the local host on
		// port 8787 with the name "jmxrmi". 
		//
		// The port specified in "service:jmx:rmi://"+hostname+":"+port
		// is the second port, where RMI connection objects will be exported.
		// Here we use the same port as that we choose for the RMI registry.
		// The port for the RMI registry is specified in the second part
		// of the URL, in "rmi://"+hostname+":"+port
		//
		final String hostname = InetAddress.getLocalHost().getHostAddress();
		JMXServiceURL url = new JMXServiceURL(
				"service:jmx:rmi://" + hostname + ":" + jmxPort + "/jndi/rmi://" + hostname + ":" + jmxPort + "/jmxrmi");

		// Now create the server from the JMXServiceURL
		//
		JMXConnectorServer cs = JMXConnectorServerFactory.newJMXConnectorServer(url, env, mbs);

		// Start the RMI connector server.
		//
		LOG.info("Start the RMI connector server on port {}",  jmxPort);
		cs.start();
	}	
	
	private void forceShutdown(){
		this.executor.shutdownNow();
		this.timerService.stop();
		LOG.info("Force shutting down {}", this.systemName);
	}
	
	/**
	 * upon closing of a system, we want to:
	 * 1) terminate admin service actor
	 * 2) admin service actor terminates its children
	 * 3) admin service actor notifies remote admin service actor of its
	 *    termination
	 */
	@Override
	public void close() {
		final long parkTime = LUNAR_SERVICE_SHUTDOWN_POLL_FREQ_IN_MS * 1_000_000;

		// if start is in progress, wait a while for it to get started.
		// force shutdown
		final long timeout = timerService.nanoTime() + LUNAR_SERVICE_SHUTDOWN_POLL_FREQ_IN_MS * 1_000_000 * 10; 
		while (!this.currentState.compareAndSet(LunarSystemState.STARTED, LunarSystemState.STOP_IN_PROGRESS)){
			LOG.info("Waiting for system to start before shutting it down...");
			LockSupport.parkNanos(parkTime);
			if (timerService.nanoTime() > timeout){
				forceShutdown();
				return;
			}
			
		}

		LOG.info("Shutting down {}", this.systemName);
		
		// it is possible that the shutdown is called before it is running
		while (!adminService.hasThreadStarted()){
			LOG.info("Waiting for disruptor to start before shutting it down");
			LockSupport.parkNanos(parkTime);
		}
		
		disruptor.shutdown();
		executor.shutdown();
		
		// poll admin service status
		while (!adminService.isStopped() || adminService.hasThreadStarted()){
			LockSupport.parkNanos(parkTime);
			LOG.info("Waiting for admin to stop completely [isStopped:{}, hasThreadStarted:{}]",
					adminService.isStopped(),
					adminService.hasThreadStarted());
		}

		timerService.stop();

		// TODO break on timeout
		// check executor status
		while (!executor.isShutdown()){
			LOG.info("Waiting for executor to shutdown");
			LockSupport.parkNanos(parkTime);
		}
		
		if (!this.currentState.compareAndSet(LunarSystemState.STOP_IN_PROGRESS, LunarSystemState.STOPPED)){
			throw new IllegalStateException("cannot mark a process as stopped, the current state is " + this.currentState.get().name());
		}

		LOG.info("Shutdown of {} completed", this.systemName);
	}
	
	public LunarSystemState state(){
		return this.currentState.get();
	}
	
	public MessageSinkRefMgr getMessageSinkRefMgr() {
		return messageSinkRefMgr;
	}
	
	private static class AdminServiceDisruptorExceptionHandler implements ExceptionHandler<MutableDirectBuffer> {
		private static final Logger LOG = LogManager.getLogger(AdminServiceDisruptorExceptionHandler.class);
		private final String name;
		public AdminServiceDisruptorExceptionHandler(String name){
			this.name = name;
		}
		@Override
		public void handleEventException(Throwable ex, long sequence, MutableDirectBuffer event) {
			LOG.error("caught exception on processing event: " + name, ex);
		}
		@Override
		public void handleOnStartException(Throwable ex) {
			LOG.error("caught exception on start: " + name, ex);
		}
		@Override
		public void handleOnShutdownException(Throwable ex) {
			LOG.error("caught exception for: " + name, ex);
		}
	}
	
	public boolean isCompletelyShutdown(){
		// TODO perform checks on whether resources have been released successfully
		if (timerService.isActive()){
			return false;
		}
		return true;
	}

	@Override
	public void startDashboard() {
		// Make use of TimerService to send event to AdminService
		LOG.info("Received JMX command to start dashboard");
		TimeoutHandlerTimerTask createTimerTask = timerService.createTimerTask(startDashboardTaskTimer, "start-dashboard");
		timerService.newTimeout(createTimerTask, 1, TimeUnit.MILLISECONDS);
	}
	
	@Override
	public void stopDashboard() {
		// Make use of TimerService to send event to AdminService
		LOG.info("Received JMX command to stop dashboard");
		TimeoutHandlerTimerTask createTimerTask = timerService.createTimerTask(stopDashboardTaskTimer, "stop-dashboard");
		timerService.newTimeout(createTimerTask, 1, TimeUnit.MILLISECONDS);
	}

	private final TimeoutHandler startDashboardTaskTimer = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {			
			long result = timerEventSender.sendTimerEvent(messageSinkRefMgr.admin(), AdminService.CLIENT_KEY_FOR_START_DASHBOARD, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				LOG.error("Could not start dashboard");
			}
		}
	};
	
    private final TimeoutHandler stopDashboardTaskTimer = new TimeoutHandler() {
		
		@Override
		public void handleTimeoutThrowable(Throwable ex) {
			LOG.error("Caught throwable", ex);
		}
		
		@Override
		public void handleTimeout(TimerEventSender timerEventSender) {			
			long result = timerEventSender.sendTimerEvent(messageSinkRefMgr.admin(), AdminService.CLIENT_KEY_FOR_STOP_DASHBOARD, TimerEventType.TIMER, TimerEventSbeEncoder.startTimeNullValue(), TimerEventSbeEncoder.expiryTimeNullValue());
			if (result != MessageSink.OK){
				// Retry in a moment
				LOG.error("Could not stop dashboard");
			}
		}
	};

}
