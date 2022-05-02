package com.lunar.perf.latency;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.beust.jcommander.JCommander;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.fsm.service.lunar.LunarService;
import com.lunar.perf.PerformanceTestHelper;
import com.lunar.perf.latency.LatencyTestCommand.WaitStrategyType;
import com.lunar.service.ServiceConstant;

import org.agrona.MutableDirectBuffer;

public class LatencyTest {
	private static final Logger LOG = LogManager.getLogger(LatencyTest.class);
	private static final long EXECUTOR_SHUTDOWN_TIMEOUT_MS = 1_000;

	private HeadNode headNode;
	private final List<Node> nodes;
	private final LatencyTestContext context;
	private final List<ExecutorService> executors;
	private final List<Disruptor<MutableDirectBuffer>> disruptors;
	
    public static void main(final String[] args) throws Exception
    {
    	LatencyTestCommand command = new LatencyTestCommand();
    	if (args.length != 0){
    		new JCommander(command, args);
    	}
    	else {
    		command.build("manual").iterations(500_000).name("test").runs(5).persistOutput(false).pauseTimeNs(10_000).waitStrategyType(WaitStrategyType.BUSY_SPIN).numNodes(3);
    	}
    	
    	LatencyTest test = new LatencyTest(command);
    	test.start();
    	test.output(command.persistOutput());
    }
    
	public LatencyTest(LatencyTestCommand command){
    	// create context from argument
    	if (command.numNodes() < 2){
    		throw new IllegalArgumentException("Number of nodes must be equal to or greater than 2");
    	}
    	
    	LatencyTestContext context = LatencyTestContext.of(command);
    	
		if (context.runs() < 2){
			throw new IllegalArgumentException("Number of runs must be equal to or greater than 2");
		}
		this.context = context;
		this.nodes = new ArrayList<Node>();
		this.executors = new ArrayList<ExecutorService>();
		this.disruptors = new ArrayList<Disruptor<MutableDirectBuffer>>();
		this.numServices = command.numNodes();
		
		switch (context.threadArrangementType()){
		case SINGLE_THREAD_PER_SERVICE:
			initForSingleThreadPerService();
			break;
		case CACHED_THREAD_POOL_FOR_ALL_SERVICE:
		default:
			throw new UnsupportedOperationException("Currently not supporting " + context.threadArrangementType().name());
		}
	}
	
	private int numServices;
//	private int nextSinkId = 0;
	@SuppressWarnings({ "unchecked" })
	private void initForSingleThreadPerService(){
		LOG.info("Initializing single thread per service");
		int nextSinkId = 0;
		
		// create head node and nodes from arguments 
		final boolean useRealTimerService = true;
		final int messageQueueSize = 128;
		PerformanceTestHelper helper = new PerformanceTestHelper(context.messageSize(), useRealTimerService, numServices);

		// base on argument, create execution service
		ExecutorService executor = Executors.newSingleThreadExecutor(
				new NamedThreadFactory("perf-head", 
						"perf-head-" + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
		executors.add(executor);

		Disruptor<MutableDirectBuffer> disruptor = new Disruptor<MutableDirectBuffer>(
				helper.messageFactory().eventFactory(),
				messageQueueSize,
				executor,
				ProducerType.MULTI,
				context.waitStrategy());
		disruptors.add(disruptor);

		// create head message service
		LunarService headService = helper.createMessageService(nextSinkId++, 
				messageQueueSize, 
				true, 
				true, 
				disruptor.getRingBuffer(),
				context);
		disruptor.handleEventsWith(headService);

		headNode = new HeadNode(context, headService);

		// create node message service
		for (; nextSinkId < numServices; nextSinkId++){
			ExecutorService nodeExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("perf-node", 
					"perf-node-" + ServiceConstant.DISRUPTOR_THREAD_NAME_SUFFIX));
			executors.add(nodeExecutor);
			
			Disruptor<MutableDirectBuffer> nodeDisruptor = new Disruptor<MutableDirectBuffer>(
					helper.messageFactory().eventFactory(),
					messageQueueSize,
					nodeExecutor, 
					ProducerType.MULTI,
					context.waitStrategy());
			disruptors.add(nodeDisruptor);
			
			LunarService nodeService = helper.createMessageService(nextSinkId, 
					messageQueueSize, 
					true, 
					false, 
					nodeDisruptor.getRingBuffer(),
					context);
			nodeDisruptor.handleEventsWith(nodeService);

			Node node = new Node(nodeService);
			nodes.add(node);
		}
	}
	
	/**
	 * Blocking method
	 * @throws InterruptedException
	 * @throws BrokenBarrierException
	 */
	public void start() throws InterruptedException, BrokenBarrierException{
		// setup barrier such that all services can be ready
		final CyclicBarrier barrier = new CyclicBarrier(1 /* this */ + 1 /* head */ + nodes.size());
		context.startupBarrier(barrier);
		
		// start all disruptors
		LOG.info("Start {} disruptors", disruptors.size());
		disruptors.forEach(d -> { d.start(); });
		
		// wait for initialization
		LOG.info("Main thread is ready");
		context.readyToStart();
		
		// hookup
		Node current = this.headNode;
		for (Node node : nodes){
			current.bind().forwardTo(node);
			current = node;
		}
		nodes.get(nodes.size() - 1).bind().forwardTo(headNode);
		
		// warm-up passes
		for (int i = 0; i < context.runs() - 1; i++){
			runPass();
	        System.out.format("%s run %d - %s\n", getClass().getSimpleName(), Long.valueOf(i), context.histogram());
	        dumpHistogram(context.histogram(), System.out);
		}
		
		// actual pass
		runPass();
        System.out.format("%s run %d Sbe Disruptor %s\n", getClass().getSimpleName(), Long.valueOf(context.runs()), context.histogram());
        context.dump();
        
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
	
    private static void dumpHistogram(final Histogram histogram, final PrintStream out)
    {
    	out.println("Histogram in microseconds");
        histogram.outputPercentileDistribution(out, 1, 1000.0);
    }
    
    private void output(boolean persistOutput){
    }
    
	private void runPass() throws InterruptedException, BrokenBarrierException{
		System.gc();
		
		final CountDownLatch latch = new CountDownLatch(1);
		
		context.completionLatch(latch);
		context.histogram().reset();
		
		headNode.start();
		
		LOG.info("Waiting for end of this pass...");
		latch.await();
	}
}
