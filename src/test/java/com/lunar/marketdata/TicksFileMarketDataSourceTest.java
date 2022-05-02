package com.lunar.marketdata;

public class TicksFileMarketDataSourceTest {
	/*
	private static final int MDI_CAPACITY = 1000;
	private static final Logger LOG = LogManager.getLogger(TicksFileMarketDataSourceTest.class);	

	private int parentSinkId = 2;
	private MessageSinkRef parent;
	
	private TestHelper testHelper;
	private ExecutorService executor;

	private SpreadTable spreadTable = SpreadTableBuilder.get();
	private MarketDataInfoBitSet mdi;
	
	private BufferedWriter writer;
	
	@Before
	public void setup(){
		mdi = new CommonMarketDataInfoBitSet(MDI_CAPACITY);
		testHelper = TestHelper.of();
		executor = Executors.newSingleThreadExecutor();
		
		Charset charset = Charset.forName("US-ASCII");
		try {
			writer = Files.newBufferedWriter(FileSystems.getDefault().getPath("C:\\Users\\Andrew\\Documents\\MinGW","Orderbooks.txt"), charset);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
	
	private final Long2ObjectLinkedOpenHashMap<MarketOrderBook> obs = new Long2ObjectLinkedOpenHashMap<>();
	
	private void handleSnapshot(Frame frame, int senderSinkId, int dstSinkId, MarketDataOrderBookSnapshotSbeDecoder snapshot){
		try {
			writer.write(String.format("%d\n", snapshot.seq()));
			long secSid = snapshot.secSid();
			// find if order book already exists
			MarketOrderBook orderBook = obs.get(secSid);
			if (orderBook == null){
				orderBook = MarketOrderBook.of(10, spreadTable, -1, -1);
				obs.put(secSid, orderBook);
			}
			for (EntryDecoder entry : snapshot.entry()){						
				writer.write(LocalTime.ofNanoOfDay(entry.transactTime()) + "\n");
				SingleSideMarketOrderBook single;
				if (entry.entryType() == EntryType.BID){
					single = orderBook.bidSide();
				}
				else if (entry.entryType() == EntryType.OFFER){
					single = orderBook.askSide();
				}
				else {
					throw new IllegalArgumentException("invalid entry type: " + entry.entryType().name());
				}
				switch (entry.updateAction()){
				case CHANGE:
					single.update(entry.tickLevel(), entry.price(), entry.priceLevel(), entry.quantity(), entry.numOrders());
					break;
				case DELETE:
					single.delete(entry.tickLevel(), entry.price(), entry.priceLevel());
					break;
				case NEW:
					single.create(entry.tickLevel(), entry.price(), entry.priceLevel(), entry.quantity(), entry.numOrders());
					break;
				default:
					throw new IllegalArgumentException("invalid update action: " + entry.updateAction().name());				
				}
			}
								
			Iterator<Tick> bidIter = orderBook.bidSide().iterator();
			writer.write("BidSide:");
			while(bidIter.hasNext()) {
				Tick next = bidIter.next();						
				writer.write(" " + next.toString());						
			}
			writer.write("\n");
			
			Iterator<Tick> askIter = orderBook.askSide().iterator();
			writer.write("AskSide:");
			while(askIter.hasNext()) {
				Tick next = askIter.next();						
				writer.write(" " + next.toString());						
			}
			writer.write("\n");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}							
	}
	
 
	
	@SuppressWarnings("unchecked")
	@Test
	public void testCreate() {
		
		MessageCodec codec = testHelper.codecForTest();
        final ExecutorService cachedThreadExecutor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        Disruptor<Frame> disruptor = new Disruptor<Frame>(new MessageSinkEventFactory(1024), 1024, cachedThreadExecutor, ProducerType.MULTI, new YieldingWaitStrategy());        
        parent = MessageSinkRef.of(TestMessageSinkBuilder.createRingBufferMessageSink(parentSinkId, ServiceType.MarketDataService, disruptor.getRingBuffer()));

        TicksFileMarketDataSource mds = new TicksFileMarketDataSource(parent,
				testHelper.messagingContext(),
				mdi, 
				spreadTable);
		Future<?> submit = executor.submit(mds);
//		CompletableFuture<Void> runAsync = CompletableFuture.runAsync(mds);

		codec.decoder().addMarketDataOrderBookSnapshotHandler(this::handleSnapshot);
        
        disruptor.handleEventsWith(new EventHandler<Frame>(){
			@Override
			public void onEvent(Frame event, long sequence, boolean endOfBatch) throws Exception {			
				codec.decoder().receive(event);
			}
        	
        });
       
        disruptor.start();
        
//		ArgumentCaptor<Frame> argumentCaptor = ArgumentCaptor.forClass(Frame.class);
//		verify(parent, times(100)).publish(argumentCaptor.capture(), any(UnsafeBuffer.class));
		//runAsync.thenAccept((c) -> {});
		try {
			submit.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
		disruptor.shutdown();
		cachedThreadExecutor.shutdown();
		try {
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
*/
}
