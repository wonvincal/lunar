package com.lunar.perf.throughput;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.beust.jcommander.JCommander;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.message.Command;
import com.lunar.message.binary.Messenger;
import com.lunar.message.io.sbe.CommandType;
import com.lunar.message.io.sbe.ServiceType;
import com.lunar.message.sink.ValidNullMessageSink;
import com.lunar.perf.PerformanceTestHelper;
import com.lunar.perf.ThreadArrangementType;
import com.lunar.perf.latency.LatencyTestCommand.WaitStrategyType;
import com.lunar.service.ServiceConstant;
import com.lunar.util.ServiceTestHelper;

import org.agrona.MutableDirectBuffer;

/**
 * All our services are multi-producer single-consumer.  We need to make sure that our consuming rate is faster than
 * producing rate in order to avoid buffer overflow.  We will need to do these:
 * 1) measure maximum service rate for each consumer
 * 2) estimate maximum arrival rate (sum of all producers)
 * 3) multiply 1) and 2) should give us the required queue size
 * 
 * Mean arrival rate: A
 * Mean service rate: S
 * Mean number of customers in service: C = A / S
 * 
 * We should always know make sure each consumer can poll message (S) at a faster rate than arrival rate (A)
 * 
 * Need to study queuing theory...
 * 
 * @author wongca
 *
 */
public class ThroughputTest {
	private static final Logger LOG = LogManager.getLogger(ThroughputTest.class);
	private static final long EXECUTOR_SHUTDOWN_TIMEOUT_MS = 1_000;
	private RobberNode robberNode;
	private final List<PoliceNode> policeNodes;
	private final ThroughputTestContext context;
	private final List<ExecutorService> executors;
	private final List<Disruptor<MutableDirectBuffer>> disruptors;
	private RingBuffer<MutableDirectBuffer> robberRingBuffer; 
	private int numServices;
	private final Messenger selfMessenger;
	private final List<Result> results;
	private final String name;
	private final String build;
	private final ThreadArrangementType threadArrangementType;
	private final WaitStrategyType waitStrategyType;
	private final int messageQueueSize;
	
    public static void main(final String[] args) throws Exception
    {
    	ThroughputTestCommand command = new ThroughputTestCommand();
    	if (args.length != 0){
    		new JCommander(command, args);
    	}
    	else {
    		command.build("manual").name("test")
    		.burstIntervalNs(100_000)
    		.persistOutput(false)
    		.waitStrategyType(WaitStrategyType.BUSY_SPIN)
    		.numNodes(3)
    		.messageSize(256)
    		.messageQueueSize(1024)
    		.iterations(200_000);
    	}
    	
    	ThroughputTest test = new ThroughputTest(command);
    	test.start();
    	test.output(command.persistOutput());
    }

	public ThroughputTest(ThroughputTestCommand command){
    	// create context from argument
    	if (command.numNodes() < 2){
    		throw new IllegalArgumentException("Number of nodes must be equal to or greater than 2");
    	}
		this.name = command.name();
		this.build = command.build();
		this.threadArrangementType = command.threadArrangmentType();
		this.waitStrategyType = command.waitStrategyType();
		this.messageQueueSize = command.messageQueueSize();
    	ThroughputTestContext context = ThroughputTestContext.of(command);
		this.context = context;
		this.policeNodes = new ArrayList<PoliceNode>();
		this.executors = new ArrayList<ExecutorService>();
		this.disruptors = new ArrayList<Disruptor<MutableDirectBuffer>>();
		this.numServices = command.numNodes();
		this.results = new ArrayList<Result>();

		switch (context.threadArrangementType()){
		case SINGLE_THREAD_PER_SERVICE:
			initForSingleThreadPerService();
			break;
		case CACHED_THREAD_POOL_FOR_ALL_SERVICE:
		default:
			throw new UnsupportedOperationException("Currently not supporting " + context.threadArrangementType().name());
		}

		ServiceTestHelper testHelper = ServiceTestHelper.of();
		selfMessenger = testHelper.createMessenger(ValidNullMessageSink.of(1, ServiceType.AdminService), "self");
	}

	@SuppressWarnings("unchecked")
	private void initForSingleThreadPerService(){
		LOG.info("Initializing single thread per service");
		int nextSinkId = 0;

		// create robber service
		final boolean useRealTimerService = true;
		PerformanceTestHelper helper = new PerformanceTestHelper(context.messageSize(), useRealTimerService, numServices);

		// base on argument, create execution service
		ExecutorService executor = Executors.newSingleThreadExecutor(
				new NamedThreadFactory("perf-robber", 
						"perf-robber-" + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
		executors.add(executor);

		Disruptor<MutableDirectBuffer> robberDisruptor = new Disruptor<MutableDirectBuffer>(
				helper.messageFactory().eventFactory(),
				context.messageQueueSize(),
				executor,
				ProducerType.MULTI,
				context.waitStrategy());
		disruptors.add(robberDisruptor);
		robberRingBuffer = robberDisruptor.getRingBuffer();
		
		// create robber service
		LunarService robberService = helper.createThroughputMessageService(nextSinkId++, 
				context.messageQueueSize(), 
				true, 
				true,
				robberDisruptor.getRingBuffer(),
				context);
		robberDisruptor.handleEventsWith(robberService);
		
		robberNode = new RobberNode(robberService);
		
		// create node message service
		for (; nextSinkId < numServices; nextSinkId++){
			ExecutorService policeExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("perf-police", 
					"perf-police-" + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
			executors.add(policeExecutor);
			
			Disruptor<MutableDirectBuffer> policeDisruptor = new Disruptor<MutableDirectBuffer>(
					helper.messageFactory().eventFactory(),
					context.messageQueueSize(),
					policeExecutor, 
					ProducerType.MULTI,
					context.waitStrategy());
			disruptors.add(policeDisruptor);
			
			LunarService policeService = helper.createThroughputMessageService(nextSinkId, 
					context.messageQueueSize(), 
					true, 
					false, 
					policeDisruptor.getRingBuffer(),
					context);
			policeDisruptor.handleEventsWith(policeService);

			PoliceNode node = new PoliceNode(context, policeService);
			policeNodes.add(node);
		}
	}
	
	public void start() throws InterruptedException, BrokenBarrierException{
		// setup barrier such that all services can be ready
		final CyclicBarrier barrier = new CyclicBarrier(1 /* this */ + 1 /* robber */ + policeNodes.size());
		context.startupBarrier(barrier);

		// start all disruptors
		LOG.info("Start {} disruptors", disruptors.size());
		disruptors.forEach(d -> { d.start(); });
		
		// wait for initialization
		LOG.info("Main thread is ready");
		context.readyToStart();

		// hookup
		this.robberNode.bind();
		for (PoliceNode policeNode : policeNodes){
			policeNode.bind().aim(robberNode);
		}

		// warm-up passes
		warmup();
		
		final int initBurstSize = 5;
		final int incBurstSize = 5;
		final int maxBurstSize = 100;
		
		int initBurstIntervalNs = 100_000;
		double burstIntervalReductionFactor = 1;
		double reductionRate = 0.9;
		
		int burstIntervalNs = initBurstIntervalNs;
		final double minBurstIntervalReductionFactor = 0.5;
		while (burstIntervalReductionFactor > minBurstIntervalReductionFactor){
			for (int burstSize = initBurstSize; burstSize <= maxBurstSize; burstSize += incBurstSize){
				System.gc();

				final CountDownLatch latch = new CountDownLatch(policeNodes.size());
				context.completionLatch(latch);
				context.burstSize(burstSize);

				policeNodes.forEach(p -> {
					selfMessenger.sendCommand(p.sink(), 
							Command.of(p.sink().sinkId(), 
									selfMessenger.getNextClientKeyAndIncrement(),
									CommandType.START));
				});

				LOG.info("Waiting for end of this pass...");
				latch.await();

				// make sure there is no outstanding message in RobberService
				waitForRobberServiceToConsumeAllOutstandingMsg();

				boolean passed = context.passed();
				results.addAll(context.results());
				context.clearResults();
				if (!passed){
					break;
				}
			}
			burstIntervalReductionFactor *= reductionRate;
			burstIntervalNs *= burstIntervalReductionFactor;
			context.burstIntervalNs(burstIntervalNs);
		}
		
		LOG.info("Stop {} disruptors and executors", disruptors.size());
		disruptors.forEach(d -> { d.shutdown(); });
		executors.forEach(s -> { 
			s.shutdown();
			try {
				s.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			} 
			catch (Exception e) {
				LOG.error("Caught exception while waiting for executor to shutdown", e);
				System.exit(1);
			}
		});
	}
	
	private void warmup() throws InterruptedException {
		System.gc();
		
		final CountDownLatch latch = new CountDownLatch(policeNodes.size());
		context.warmupCompletionLatch(latch);

		LOG.info("Waiting for end of warmup...");
		policeNodes.forEach(p -> {
			selfMessenger.sendCommand(p.sink(), 
					Command.of(p.sink().sinkId(),
							selfMessenger.getNextClientKeyAndIncrement(),
							CommandType.WARMUP));
		});
		
		latch.await();
		
		waitForRobberServiceToConsumeAllOutstandingMsg();
	}
	
	private void waitForRobberServiceToConsumeAllOutstandingMsg(){
		final long parkTimeNs = 10_000_000L;
		long timeoutNs = System.nanoTime() + 100_000_000L; // timeout in 100 ms
		while (robberRingBuffer.remainingCapacity() != context.messageQueueSize()){
			LockSupport.parkNanos(parkTimeNs);
			if (System.nanoTime() > timeoutNs){
				throw new IllegalStateException("There are " + robberRingBuffer.remainingCapacity() + " outstanding messages");
			}
		}		
	}
	
    private void output(boolean persistOutput){
		System.out.println("Name: " + name);
		System.out.println("Build: " + build);
		System.out.println("Date: " + LocalDateTime.now());
		System.out.println("Message Queue Size: " + messageQueueSize);
		System.out.println("Wait Strategy: " + waitStrategyType.name());
		System.out.println("Thread Arrangement: " + threadArrangementType.name());
		System.out.println("+--------+--------------+-----------+-----------+----------+----------+--------------+---------+-----------------+-----------+-----------------+------------------+--------+");
		System.out.println("+-sinkId-+---elapsed----+-iteration-+-remaining-+-overflow-+-tooShort-+-msgQueueSize-+-msgSize-+-burstIntervalNs-+-burstSize-+-throughputPerMs-+-throughputPerSec-+-passed-+");
		System.out.println("+--------+--------------+-----------+-----------+----------+----------+--------------+---------+-----------------+-----------+-----------------+------------------+--------+");
		for (Result result : results){
			System.out.println(result.toString());
		}
		System.out.println("+--------+--------------+-----------+-----------+----------+----------+--------------+---------+-----------------+-----------+-----------------+------------------+--------+");
    }
}
