package com.lunar.message.sink;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lmax.disruptor.RingBuffer;
import com.lunar.core.MessageSinkRefList;
import com.lunar.core.TimerService;
import com.lunar.message.io.sbe.ServiceType;

import io.aeron.Publication;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * 
 * Holds all required objects to communicate within the Lunar system
 *  
 * Expected communications flows:
 * 1) Commands: admin actor <-> other actors
 * 2) Remote commands: admin actor <-> remote admin actors
 * 3) Events: ring buffer <-> ring buffer
 * 4) Remote events: 
 * a) ring buffer message sink <-> aeron message sink <-> remote aeron message sink <-> remote ring buffer message sink    
 *  
 * Thread safety: Yes.  sinks[] has no lock.
 *  
 * This class is not thread safe, make sure that it has only one write at a time.
 * The expected writers are 1) Lunar initialization, 2) AdminServiceActor, that's it.  Only one thread writes to this.
 * 
 * @author Calvin
 * @status Completed
 */
public class NormalMessageSinkRefMgr implements MessageSinkRefMgr {
	private static AtomicInteger ID = new AtomicInteger(5000);
	static final String ADMIN_MNEM = "admin";
	static final String OMES_MNEM = "omes";
	static final String RDS_MNEM = "rds";
	static final String MDS_MNEM = "mds";
	static final String MDSSS_MNEM = "mdsss";
	static final String PERF_MNEM = "perf";
	static final String STRAT_MNEM = "strat";
	static final String PERSI_MNEM = "persi";
	static final String DEADLETTER_MNEM = "dl";
	static final String RISK_MNEM = "risk";
	static final String OTSS_MNEM = "otss";
	static final String WARMUP_MNEM = "warmup";
	static final String NOTIFY_MNEM = "notify";
	static final String PRC_MNEM = "pricing";
	static final String DASHBOARD_MNEM = "dashboard";
	static final String DASHBOARDWEB_MNEM = "dashboardweb";
	static final String SCOREBOARD_MNEM = "scoreboard";
	public static final String VALID_NULL_MNEM = "valid-null";
	static final Logger LOG = LogManager.getLogger(MessageSinkRefMgr.class);
	
	// Location transparent communication
	// Groups - Admin, All Local Actors, All Local Services, All Remote Admins
	private final int systemId;
	private final int maxNumSinks;
	private final MessageSinkRef admin;
	private final MessageSinkRef omes;
	private final MessageSinkRef rds;
	private final MessageSinkRef mds;
	private final MessageSinkRef mdsss;
	private final MessageSinkRef perf;
	private final MessageSinkRefList strats;
	private final MessageSinkRef persi;
	private final MessageSinkRef risk;
	private final MessageSinkRef otss;
	private final MessageSinkRef warmup;
	private final MessageSinkRef ns;
	private final MessageSinkRef prc;
	private final MessageSinkRef dashboard;
	private final MessageSinkRef dashboardweb;
	private final MessageSinkRef deadLetters;
	private final MessageSinkRef[] sinks;
	private final TimerService timerService;
	private final MessageSinkRef warmupPerf;
	private final MessageSinkRef scoreboard;
	
	// TODO may write my own copy-on-write-array-list, because this one in JDK requires 
	// 1) creation a new array when calling toArray()
	// 2) creation of iterator when calling iterator().
	// both ways involve creating more objects.
	// I only want a plain array, which is locked when you try to modify it
	private MessageSinkRef[] localSinks; // non admin sinks
	private MessageSinkRef[] remoteSinks; // non admin sinks
	private MessageSinkRef[] remoteAdmins;

	private final Object2ObjectOpenHashMap<Publication, MessageSinkRef> publicationMessageSinkRefs;
	
	private volatile long modifyTime;
	
	/**
	 * HACK!!!!!!!!!!!!  TODO Remove
	 */
//	private Int2ObjectOpenHashMap<ServiceLifecycleAware> services;
	
	private final int id;
	
	public NormalMessageSinkRefMgr(int systemId, int capacity, TimerService timerService){
		this.id = ID.getAndIncrement();
		this.systemId = systemId;
		this.maxNumSinks = capacity;
		this.remoteSinks = new MessageSinkRef[0];
		this.localSinks = new MessageSinkRef[0];
		this.remoteAdmins = new MessageSinkRef[0];
		this.timerService = timerService;
		admin = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.AdminService, ADMIN_MNEM);
		omes = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.OrderManagementAndExecutionService, OMES_MNEM);
		rds = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.RefDataService, RDS_MNEM);
		dashboard = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.DashboardService, DASHBOARD_MNEM);
		dashboardweb = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.DashboardStandAloneWebService, DASHBOARDWEB_MNEM);
		mds = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.MarketDataService, MDS_MNEM);
		mdsss = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.MarketDataSnapshotService, MDSSS_MNEM);
		perf = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.PerformanceService, PERF_MNEM);
		warmupPerf = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.PerformanceService, PERF_MNEM);
		strats = MessageSinkRefList.of(); 				
		deadLetters = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.DeadLetterService, DEADLETTER_MNEM);
		persi = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.PersistService, PERSI_MNEM);
		risk = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.PortfolioAndRiskService, RISK_MNEM);
		otss = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.OrderAndTradeSnapshotService, OTSS_MNEM);
		warmup = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.WarmupService, WARMUP_MNEM);
		ns = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.NotificationService, NOTIFY_MNEM);
		prc = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.PricingService, PRC_MNEM);
		scoreboard = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.ScoreBoardService,  SCOREBOARD_MNEM);
		sinks = new MessageSinkRef[capacity];
		for (short i = 0; i < capacity; i++){
			sinks[i] = MessageSinkRef.createValidNullSinkRef(this.systemId, ServiceType.NULL_VAL, NULL_MNEM);
		}
		this.modifyTime = timerService.nanoTime();
		this.publicationMessageSinkRefs = new Object2ObjectOpenHashMap<>();
	}	

	public void registerDeadLetters(MessageSinkRef ref){
		deadLetters.messageSink(ref.underlyingSink());
	}

	@Override
	public int systemId(){
		return systemId;
	}

	@Override
	public int maxNumSinks(){
		return maxNumSinks;
	}
	
	synchronized private void addLocalSink(MessageSinkRef sink){
		// check if ref is found. if yes, replace.  if no, append.
		MessageSinkRef[] refs = localSinks;
		for (int i = 0; i < refs.length; i++){
			MessageSinkRef ref = refs[i];
			if (ref.equals(sink)){
				// replace it, possibly throw exception if thing doesn't look right
				// this is an atomic thread-safe operation
				refs[i] = sink;
				return;
			}
		}
		// append to the end
		MessageSinkRef[] newRefs = Arrays.copyOf(refs, refs.length + 1);
		newRefs[refs.length] = sink;

		// this is an atomic thread-safe operation
		localSinks = newRefs;
	}

	synchronized private void addRemoteSink(MessageSinkRef sink){
		// check if ref is found. if yes, replace.  if no, append.
		MessageSinkRef[] refs = remoteSinks;
		for (int i = 0; i < refs.length; i++){
			MessageSinkRef ref = refs[i];
			if (ref.equals(sink)){
				// replace it, possibly throw exception if thing doesn't look right
				// this is an atomic thread-safe operation
				refs[i] = sink;
				return;
			}
		}
		// append to the end
		MessageSinkRef[] newRefs = Arrays.copyOf(refs, refs.length + 1);
		newRefs[refs.length] = sink;

		// this is an atomic thread-safe operation
		modifyTime = timerService.nanoTime();
		remoteSinks = newRefs;
	}
	
	synchronized private void addRemoteAdmin(MessageSinkRef sink){
		// check if reference is found. if yes, replace.  if no, append.
		MessageSinkRef[] refs = remoteAdmins;
		for (int i = 0; i < refs.length; i++){
			MessageSinkRef ref = refs[i];
			if (ref.equals(sink)){
				// replace it, possibly throw exception if thing doesn't look right
				// this is an atomic thread-safe operation
				refs[i] = sink;
				return;
			}
		}
		// append to the end
		MessageSinkRef[] newRefs = Arrays.copyOf(refs, refs.length + 1);
		newRefs[refs.length] = sink;

		// this is an atomic thread-safe operation
		modifyTime = timerService.nanoTime();
		remoteAdmins = newRefs;
	}
	
	@Override
	public MessageSinkRef admin(){
		return admin;
	}

	@Override
	public MessageSinkRef rds(){
		return rds;
	}

	@Override
	public MessageSinkRef dashboard(){
		return dashboard;
	}
	
	@Override
	public MessageSinkRef scoreboard() {
	    return scoreboard;
	}
	
	/**
	 * To be retired because there will be more than one mds
	 * @return
	 */
	@Override
	public MessageSinkRef mds(){
		return mds;
	}
	
	@Override
	public MessageSinkRef mdsss() {
		return mdsss;
	}

	/**
	 * To be retired because there will be more than one omes
	 * @return
	 */
	@Override
	public MessageSinkRef omes(){
		return omes;
	}

	@Override
	public MessageSinkRef otss(){
		return otss;
	}

	@Override
	public MessageSinkRef perf(){
		return perf;
	}
	
	@Override
	public MessageSinkRef risk(){
		return risk;
	}

	@Override
	public MessageSinkRefList strats() {
	    return strats;
	}

	@Override
	public MessageSinkRef deadLetters(){
		return deadLetters;
	}
	
	@Override
	public MessageSinkRef persi() {
	    return persi;
	}
	
	@Override
	public MessageSinkRef warmup() {
	    return warmup;
	}

	@Override
	public MessageSinkRef ns() {
	    return ns;
	}

	@Override
	public MessageSinkRef prc() {
	    return prc;
	}
	
	@Override
	public MessageSinkRef get(int sinkId){
		return sinks[sinkId];
	}

	public long volatileModifyTime(){
		return modifyTime;
	}

	@Override
	public MessageSinkRef createAndRegisterAeronMessageSink(int systemId, int sinkId, ServiceType serviceType, String name, Publication publication){
		MessageSinkRef ref = register(MessageSinkRef.of(new AeronMessageSink(systemId, sinkId, serviceType, publication), name));
		publicationMessageSinkRefs.put(publication, ref);
		return ref;
	}
	
	@Override
	public MessageSinkRef createAndRegisterRingBufferMessageSink(int sinkId, ServiceType serviceType, String name, RingBuffer<MutableDirectBuffer> ringBuffer){
		return register(MessageSinkRef.of(new RingBufferMessageSink(systemId, sinkId, serviceType, name, ringBuffer), name));
	}

	/**
	 * For testing use to inject MessageSink mocks
	 * @param sinkId
	 * @param serviceType
	 * @param name
	 * @param sink
	 * @return
	 */
	MessageSinkRef createAndRegisterMockMessageSink(int sinkId, ServiceType serviceType, String name, MessageSink sink){
		return register(MessageSinkRef.of(sink, name));
	}
	
	/**
	 * TODO Remove public visibility, limit this to be used by testing only
	 * @param sink
	 * @return
	 */
	@Override
	public MessageSinkRef register(MessageSinkRef sink){
		return register(sink.underlyingSink()); 
	}
	
	@Override
	public void deactivateBySystem(int systemId){
		LOG.info("Deactivate all sinks of system [systemId:{}]", systemId);
		for (MessageSinkRef sink : this.sinks){
			if (sink.systemId() == systemId){
				deactivate(sink);
			}
		}
	}
	
	/**
	 * Swap the underlying sink with a null sink or a dead letter sink
	 * @param sink
	 * @return
	 */
	@Override
	public MessageSinkRef deactivate(MessageSinkRef sink){
		switch (sink.serviceType()){
		case AdminService:
			if (sink.systemId() != systemId){
				admin.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
				LOG.info("deactivate admin sink: {}", sink);
			}
			break;
		case DashboardService:
			dashboard.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate dashboard sink: {}", sink);
			break;
		case DashboardStandAloneWebService:
			dashboardweb.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate dashboardweb sink: {}", sink);
			break;
	 	case MarketDataService:
	 		mds.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate market data sink: {}", sink);
			break;
	 	case MarketDataSnapshotService:
	 		mdsss.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
	 		LOG.info("deactivate market data snapshot sink: {}", sink);
			break;
	 	case NotificationService:
	 		ns.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
	 		LOG.info("deactivate notification sink: {}", sink);
			break;
		case OrderManagementAndExecutionService:
			omes.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate order management sink: {}", sink);
			break;
		case OrderAndTradeSnapshotService:
			otss.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate order and trade snapshot sink: {}", sink);
			break;
		case RefDataService:
			rds.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate reference data sink: {}", sink);
			break;
		case PerformanceService:
			perf.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate perf sink: {}", sink);
			break;
		case PortfolioAndRiskService:
			risk.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
			LOG.info("deactivate risk sink: {}", sink);
			break;			
		case StrategyService:
			// Remove sink from list of strats
			if (strats.remove(sink)){
			    LOG.info("deactivate strategy sink: {}", sink);				
			}
			else {
				LOG.error("wanted to deactivate strategy sink, but could not find it: {}", sink);
			}
		    break;
		case PersistService:
			persi.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
		    LOG.info("deactivate persist sink: {}", sink);
		    break;
	 	case WarmupService:
	 		warmup.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
	 		LOG.info("deactivate warmup sink: {}", sink);
			break;
		case PricingService:
            prc.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
            LOG.info("deactivate pricing sink: {}", sink);
            break;
		case ScoreBoardService:
		    scoreboard.messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
		    LOG.info("deactivate scoreboard sink: {}", sink);
		    break;
		default:
			break;
		}
		sinks[sink.sinkId()].messageSink(ValidNullMessageSink.of(systemId, sink.serviceType()));
		modifyTime = timerService.nanoTime();
		return sink;
	}
	
	/**
	 * 
	 * @param sink
	 * @return MessageSinkRef
	 */
	protected MessageSinkRef register(MessageSink sink){
		if (sink.sinkId() >= sinks.length){
			throw new IllegalArgumentException("Exceed maximum allowable sinkId, you may want to change numServiceInstances in config [sinkId:" + 
					sink.sinkId() + ", maxAllowableSinkId:" + (sinks.length - 1) + "]");
		}
		MessageSinkRef ref = sinks[sink.sinkId()].messageSink(sink);
		
		boolean isRemote = (sink.systemId() != systemId);
		switch (sink.serviceType()){
		case AdminService:
			if (!isRemote){
				admin.messageSink(sink);
				LOG.info("merged admin sink into message sink ref mgr: {}", sink);
			}
			break;
		case DashboardService:
			dashboard.messageSink(sink);
			LOG.info("merged dashboard sink into message sink ref mgr: {}", sink);
			break;
		case DashboardStandAloneWebService:
			dashboardweb.messageSink(sink);
			LOG.info("merged dashboard web sink into message sink ref mgr: {}", sink);
			break;
		case DeadLetterService:
			deadLetters.messageSink(sink);
			LOG.info("merged dead letter sink into message sink ref mgr: {}", sink);
			break;
 		case MarketDataService:
			mds.messageSink(sink);
			LOG.info("merged market data sink into message sink ref mgr: {}", sink);
			break;
 		case MarketDataSnapshotService:
 			mdsss.messageSink(sink);
			LOG.info("merged market data snapshot sink into message sink ref mgr: {}", sink);
			break;
 		case NotificationService:
			ns.messageSink(sink);
			LOG.info("merged notification sink into message sink ref mgr: {}", sink);
			break;
		case OrderManagementAndExecutionService:
			omes.messageSink(sink);
			LOG.info("merged order management sink into message sink ref mgr: {}", sink);
			break;
		case OrderAndTradeSnapshotService:
			otss.messageSink(sink);
			LOG.info("merged order trade snapshot sink into message sink ref mgr: {}", sink);
			break;
		case PerformanceService:
			perf.messageSink(sink);
			LOG.info("merged perf sink into message sink ref mgr: {}", sink);
			break;
		case PortfolioAndRiskService:
			risk.messageSink(sink);
			LOG.info("merged risk sink into message sink ref mgr: {}", sink);
			break;
		case RefDataService:
			rds.messageSink(sink);
			LOG.info("merged reference data sink into message sink ref mgr: {}", sink);
			break;
		case StrategyService:
			if (strats.add(ref)){
				LOG.info("merged strategy sink into message sink ref mgr: {}", sink);
			}
			else {
				LOG.info("wanted to merge strategy sink into message sink ref mgr, but already merged: {}", sink);
			}
		    break;
		case PersistService:
		    persi.messageSink(sink);
		    LOG.info("merged persist sink into message sink ref mgr: {}", sink);
		    break;
		case WarmupService:
		    warmup.messageSink(sink);
		    LOG.info("merged warmup sink into message sink ref mgr: {}", sink);
		    break;
		case PricingService:
		    prc.messageSink(sink);
            LOG.info("merged pricing sink into message sink ref mgr: {}", sink);
            break;
		case ScoreBoardService:
		    scoreboard.messageSink(sink);
            LOG.info("merged scoreboard sink into message sink ref mgr: {}", sink);
            break;
		default:
			break;
		}
		
		// TODO make sure we compare if the value has changed before we update the maps
		if (sink.serviceType() != ServiceType.AdminService){
			if (isRemote){
				addRemoteSink(ref);
			}
			else{
				addLocalSink(ref);
			}
		}
		else {
			if (isRemote){
				addRemoteAdmin(ref);
			}
		}

		// TODO make sure we update this value only when something has been updated
		modifyTime = timerService.nanoTime();
		return ref;
	}
	
	/* (non-Javadoc)
	 * @see com.lunar.message.sink.MessagingContext#sinks()
	 */
	public MessageSinkRef[] sinks(){
		return sinks;
	}
		
	@Override
	public MessageSinkRef[] localSinks(){
		return localSinks;
	}

	@Override
	public MessageSinkRef[] remoteSinks(){
		return remoteSinks;
	}

	@Override
	public MessageSinkRef[] remoteAdmins(){
		return remoteAdmins;
	}
	
	/**
	 * Create a MessageSinkRefMgr for warmup purpose.
	 * 
	 * Replaces all sinks except self and local admin with warmup
	 * 
	 * @param selfSinkId
	 * @return
	 */
	public MessageSinkRefMgr createWarmupRefMgr(int selfSinkId, MessageSinkRef warmupSinkRef){
		
		MessageSinkRefMgr warmupRefMgr = new NormalMessageSinkRefMgr(systemId, maxNumSinks, timerService);
		for (int i = 0; i < this.sinks.length; i++){
			MessageSinkRef sinkRef = sinks[i];
			
			// Swap sinkRef with warmup sink if 
			// 1) sinkRef is not self
			// 2) sinkRef is not WarmupService
			// 3) sinkRef is not local AdminService
			// 4) sinkRef is not performance service (no need to warmup performance service, leave the reference as a null message sink)
			if (sinkRef.sinkId() != selfSinkId && 
					!sinkRef.serviceType().equals(ServiceType.WarmupService) && 
					!(sinkRef.serviceType().equals(ServiceType.AdminService) && sinkRef.systemId() == systemId) &&
					!(sinkRef.serviceType().equals(ServiceType.DeadLetterService)) &&
					!(sinkRef.serviceType().equals(ServiceType.NULL_VAL)) &&
					!(sinkRef.serviceType().equals(ServiceType.PerformanceService))){
				MessageSinkRef clone = MessageSinkRef.cloneWithSpecificSink(sinks[i], warmupSinkRef.underlyingSink());
				warmupRefMgr.register(clone);
			}
		}
		warmupRefMgr.register(this.admin);
		warmupRefMgr.register(this.deadLetters);		
		warmupRefMgr.register(get(selfSinkId));		
		warmupRefMgr.register(warmupSinkRef);
		
		return warmupRefMgr;
	}
	
	@Override
	public int id() {
		return id;
	}
}
