package performance.marketdata;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.BrokenBarrierException;
import java.util.function.BiConsumer;

import org.HdrHistogram.Histogram;

import com.lunar.marketdata.SpreadTable;
import com.lunar.marketdata.SpreadTableBuilder;
import com.lunar.marketdata.archive.BidMarketOrderBookWithProxy;
import com.lunar.marketdata.archive.SingleSideMarketOrderBook;
import com.lunar.message.io.sbe.SecurityType;

public class SingleSideMarketOrderBookLatencyTest {
    private final Histogram histogram;
    private final SpreadTable spreadTable;

    public static void main(final String[] args) throws Exception
    {
        final SingleSideMarketOrderBookLatencyTest test = new SingleSideMarketOrderBookLatencyTest();
        test.start();
    }

    public SingleSideMarketOrderBookLatencyTest(){
    	histogram = new Histogram(10000000000L, 4);
    	spreadTable = SpreadTableBuilder.get(SecurityType.WARRANT);
    	methods = new TestMethod[3];
    	methods[0] = new TestMethod("runCreateAndUpdateTenLevels", this::runCreateAndUpdateTenLevels);
    	methods[1] = new TestMethod("runCreateBestAndDeleteBest", this::runCreateBestAndDeleteBest);
    	methods[2] = new TestMethod("runCreateBestAndDeleteBestTillEmpty", this::runCreateBestAndDeleteBestTillEmpty);
    }
    
    private final TestMethod[] methods;
    private static class TestMethod {
    	private final String name;
    	private final BiConsumer<SingleSideMarketOrderBook, Integer> method;
    	private final HashMap<String, Long> result;
    	
    	private TestMethod(String name, BiConsumer<SingleSideMarketOrderBook, Integer> method){
    		this.name = name;
    		this.method = method;
    		this.result = new HashMap<String, Long>();
    	}
    	public String name(){ return name;}
    	public BiConsumer<SingleSideMarketOrderBook, Integer> method(){ return method;}
    	public void putResult(String name, Long ns){
    		result.put(name, ns);
    	}
    	public HashMap<String, Long> result(){
    		return result;
    	}
    	
    }
    public void start() throws Exception
    {
    	String pattern = "###,###.###";
    	DecimalFormat decimalFormat = new DecimalFormat(pattern);
    	
        final int runs = 30;
        int warmUpRuns = 30;
        int numTries = 3_000_000;
        int bookDepth = 10;
		int nullTickLevel = -1;
		int nullPrice = -1;
		int numOrderBookTypes = 2;
		
		String[] obNames = new String[numOrderBookTypes];
		SingleSideMarketOrderBook[] ob = new SingleSideMarketOrderBook[numOrderBookTypes];
		ob[1] = SingleSideMarketOrderBook.forBid(bookDepth, spreadTable, nullTickLevel, nullPrice);
		obNames[1] = "BidMarketOrderBook";
//		ob[0] = new BidMarketOrderBookWithBranchDeleteWithColdHot(bookDepth, spreadTable, nullTickLevel, nullPrice);
//		obNames[0] = "BidMarketOrderBookWithBranchDeleteWithColdHot";
//		ob[0] = SingleSideMarketOrderBook.forPriceLevelBased(bookDepth, spreadTable, nullTickLevel, nullPrice);
//		obNames[0] = "PriceLevelBasedMarketOrderBook";
//		ob[0] = new BidMarketOrderBookWithNoDeleteProxy(bookDepth, spreadTable, nullTickLevel, nullPrice);
//		obNames[0] = "BidMarketOrderBookWithNoDeleteProxy";
//		ob[0] = new BidMarketOrderBookWithBranchDelete(bookDepth, spreadTable, nullTickLevel, nullPrice);
//		obNames[0] = "BidMarketOrderBookWithBranchDelete";
		ob[0] = new BidMarketOrderBookWithProxy(bookDepth, spreadTable, nullTickLevel, nullPrice);
		obNames[0] = "BidMarketOrderBookWithProxy";
        
		LocalTime from = LocalTime.now();
		long testStartTime = System.nanoTime();
		
		// for each methods, test with different order book types
		// for each order book type, test for N runs
        for (int m = 0; m < methods.length; m++){
        	for (int b = 0; b < ob.length; b++){
                for (int i = 0; i < warmUpRuns; i++)
                {
        			methods[m].method().accept(ob[b], numTries);
                }
                System.gc();
        		long ns = 0;
                for (int i = 0; i < runs; i++)
                {
                    System.gc();
                    histogram.reset();

        			long start = System.nanoTime();
        			methods[m].method().accept(ob[b], numTries);
        			long end = System.nanoTime();
        			ns += (end - start);
                }
                methods[m].putResult(obNames[b], ns / runs);
        	}
        }
        
        long testEndTime = System.nanoTime();
        LocalTime end = LocalTime.now();

        System.out.println("Result for warmupRuns: " + warmUpRuns + ", numRuns: " + runs +", numTries: " + decimalFormat.format(numTries) + " per test, bookDepth: " + bookDepth);
        System.out.println("runDate: " + LocalDateTime.now());
        System.out.println("wall clock: from: " + from + " to: " + end);
        System.out.println("took: " + decimalFormat.format(testEndTime - testStartTime) + " ns");
        System.out.println("===================================================================");
        for (int m = 0; m < methods.length; m++){
        	System.out.format("[%s]: average latency\n", methods[m].name());
        	Entry<String, Long> maxEntry = null;
        	Entry<String, Long> minEntry = null;
        	for (Entry<String, Long> entry : methods[m].result().entrySet()){
        		if  (maxEntry == null || maxEntry.getValue() < entry.getValue()){
        			maxEntry = entry;
        		}
        		if (minEntry == null || minEntry.getValue() > entry.getValue()){
        			minEntry = entry;
        		}
            	System.out.println(entry.getKey() + ": " + decimalFormat.format(entry.getValue()) + " ns");
        	}
        	System.out.println(minEntry.getKey() + " is " + decimalFormat.format(((double)maxEntry.getValue() / minEntry.getValue())) + "x of " + maxEntry.getKey());
        	System.out.println();
        }
//			runCreateAndUpdateTenLevels(ob, numTries);
			
			// forBid
			// 49 run took 909530616 ns - 56 MB - with no branching in determining max
			// 49 run took 999095083 ns - with branching
			// 19 run took 981069888 ns - with no branching, BitUtil.max
			// 19 run took 976462769 ns - with no branching, but use () ? a : b
			// 19 run took 976462769 ns - with no branching, BitUtil.max at both places
			// 48 run took 65055608 ns - 56 MB (fixed delete)
			// 48 run took 60459608 ns
			
			// forPLB
			// 48 run took 121340028 ns - 163 MB
			
			// forBid
			// 48 run took 169709426 ns - 56 MB
			
			// forPBL
			// 48 run took 152500194 ns - 267 MB
//			runCreateBestAndDeleteBest(ob, numTries);
//			long end = System.nanoTime();

//			System.out.format("%s run took %d ns\n", i, end - start);
//            System.out.format("%s run %d %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
//            dumpHistogram(histogram, System.out);
//        }
//        }
    }

    @SuppressWarnings("unused")
	private static void dumpHistogram(final Histogram histogram, final PrintStream out)
    {
    	out.println("Histogram in microseconds");
        histogram.outputPercentileDistribution(out, 1, 1000.0);
    }

    private void runCreateAndUpdateTenLevels(SingleSideMarketOrderBook ob, int numUpdates){
    	int price1 = 9750;
    	int tickLevel1 = spreadTable.priceToTick(price1);
    	int priceLevel1 = 10;
    	
    	int price2 = 9760;
    	int tickLevel2 = spreadTable.priceToTick(price2);
    	int priceLevel2 = 9;

    	int price3 = 9770;
    	int tickLevel3 = spreadTable.priceToTick(price3);
    	int priceLevel3 = 8;

    	int price4 = 9780;
    	int tickLevel4 = spreadTable.priceToTick(price4);
    	int priceLevel4 = 7;

    	int price5 = 9790;
    	int tickLevel5 = spreadTable.priceToTick(price5);
    	int priceLevel5 = 6;

    	int price6 = 9800;
    	int tickLevel6 = spreadTable.priceToTick(price6);
    	int priceLevel6 = 5;

    	int price7 = 9810;
    	int tickLevel7 = spreadTable.priceToTick(price7);
    	int priceLevel7 = 4;

    	int price8 = 9820;
    	int tickLevel8 = spreadTable.priceToTick(price8);
    	int priceLevel8 = 3;

    	int price9 = 9830;
    	int tickLevel9 = spreadTable.priceToTick(price9);
    	int priceLevel9 = 2;

    	int price10 = 9840;
    	int tickLevel10 = spreadTable.priceToTick(price10);
    	int priceLevel10 = 1;

    	int qty = 100;
    	int numOrder = 1;
    	int bookDepth = 10;
    	ob.create(tickLevel10, price10, priceLevel10, qty, numOrder);
    	ob.create(tickLevel9, price9, priceLevel9, qty, numOrder);
    	ob.create(tickLevel8, price8, priceLevel8, qty, numOrder);
    	ob.create(tickLevel7, price7, priceLevel7, qty, numOrder);
    	ob.create(tickLevel6, price6, priceLevel6, qty, numOrder);
    	ob.create(tickLevel5, price5, priceLevel5, qty, numOrder);
    	ob.create(tickLevel4, price4, priceLevel4, qty, numOrder);
    	ob.create(tickLevel3, price3, priceLevel3, qty, numOrder);
    	ob.create(tickLevel2, price2, priceLevel2, qty, numOrder);
    	ob.create(tickLevel1, price1, priceLevel1, qty, numOrder);
    	
    	for (int i = 0; i < numUpdates; i++){
    		ob.update(tickLevel1, price1, priceLevel1, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel2, price2, priceLevel2, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel3, price3, priceLevel3, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel4, price4, priceLevel4, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel5, price5, priceLevel5, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel6, price6, priceLevel6, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel7, price7, priceLevel7, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel8, price8, priceLevel8, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel9, price9, priceLevel9, qty * (i % bookDepth), numOrder * (i % bookDepth));
    		ob.update(tickLevel10, price10, priceLevel10, qty * (i % bookDepth), numOrder * (i % bookDepth));
    	}
    }

    /**
     * This should favor price-level-based order book
     * @param ob
     * @param numTries
     */
    private void runCreateBestAndDeleteBest(SingleSideMarketOrderBook ob, int numTries){
    	int price1 = 9750;
    	int tickLevel1 = spreadTable.priceToTick(price1);
    	
    	int price2 = 9760;
    	int tickLevel2 = spreadTable.priceToTick(price2);
    	
    	int price3 = 9770;
    	int tickLevel3 = spreadTable.priceToTick(price3);

    	int qty = 100;
    	int numOrder = 1;
    	
    	ob.create(tickLevel1, price1, 1, qty, numOrder);
    	for (int i = 0; i < numTries; i++){
	    	// the most time consuming create is to create above current best
	    	ob.create(tickLevel2, price2, 1, qty, numOrder);
	    	ob.create(tickLevel3, price3, 1, qty, numOrder);
	
	    	// delete best
	    	ob.delete(tickLevel3, price3, 1);
	    	ob.delete(tickLevel2, price2, 1);
    	}
    	ob.delete(tickLevel1, price1, 1);
    }
    
    /**
     * This should favor price-level-based order book
     * @param ob
     * @param numTries
     */
    private void runCreateBestAndDeleteBestTillEmpty(SingleSideMarketOrderBook ob, int numTries){
    	int price1 = 9750;
    	int tickLevel1 = spreadTable.priceToTick(price1);
    	
    	int price2 = 9760;
    	int tickLevel2 = spreadTable.priceToTick(price2);
    	
    	int price3 = 9770;
    	int tickLevel3 = spreadTable.priceToTick(price3);

    	int qty = 100;
    	int numOrder = 1;
    	
    	for (int i = 0; i < numTries; i++){
	    	// the most time consuming create is to create above current best
        	ob.create(tickLevel1, price1, 1, qty, numOrder);
	    	ob.create(tickLevel2, price2, 1, qty, numOrder);
	    	ob.create(tickLevel3, price3, 1, qty, numOrder);
	
	    	// delete best
	    	ob.delete(tickLevel3, price3, 1);
	    	ob.delete(tickLevel2, price2, 1);
	    	ob.delete(tickLevel1, price1, 1);
    	}
    }

    @SuppressWarnings("unused")
	private void runPass() throws InterruptedException, BrokenBarrierException
    {
    	// create 10 levels
    	// update 10 levels N times
    	
    	// create 10 levels
    	// update top two levels N times
    	// create two level above
    	// update top two levels N times
    	// repeat 10 times
    	
    	// simulate gradual up trend
    	// create 10 levels
    	// update top two levels
    	// create top + 1 level - a
    	// update top two levels - b
    	// delete top 1 level - c
    	// repeat a, b, c - N times
    	
    	// simulate gradual down trend
    	// create two levels
    	// update top two levels - a
    	// delete top 1 level - b
    	// create top - 1 level - c
    	// repeat a, b, c - N times
    	
    }
}
