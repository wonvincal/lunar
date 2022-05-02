package com.lunar.marketdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.lunar.concurrent.NamedThreadFactory;
import com.lunar.core.SystemClock;
import com.lunar.message.io.sbe.MarketStatusType;
import com.lunar.service.ServiceConstant;

public class MarketDataReplayer {
    private static final Logger LOG = LogManager.getLogger(MarketDataReplayer.class);
    private static final int MAX_TICKS_PER_SECOND = 60000;
    private static final int MIN_NANO_DELAY_BETWEEN_TICKS = 1000000000 / MAX_TICKS_PER_SECOND;

    private static final long OI_START_TIME_NANO = LocalTime.of(9, 0).toNanoOfDay();
    private static final long CT_START_TIME_1_NANO = LocalTime.of(9, 30).toNanoOfDay();
    private static final long EI_START_TIME_NANO = LocalTime.of(12, 0).toNanoOfDay();
    private static final long CT_START_TIME_2_NANO = LocalTime.of(13, 0).toNanoOfDay();
    private static final long DC_START_TIME_NANO = LocalTime.of(16, 0).toNanoOfDay();

    public interface TradeHandler {
        void onTradeMessageReceived(long scty, long timestamp, int price, int quantity, int tradeType);
    }

    public interface AggregateOrderBookUpdateHandler {
        void onOrderUpdate(String scty, long timestamp, String action, String side, int price, int quantity, int numOrders, byte priceLevel, int tickLevel, boolean isLast, int numOfEntries);
    }
    
    public interface OrderBookSnapshotHandler {
        void onOrderUpdate(long scty, long timestamp, int numBids, int[] bidPrices, int[] bidQuantities, int numAsks, int[] askPrices, int[] askQuantities);
    }    

    public interface MarketStatusHandler {
        void onMarketStatusChanged(String exchangeCode, MarketStatusType status);
    }

    abstract private class MarketDataSource {
        private long m_prevTimestamp = 0;
        private int m_playSpeed = 0;

        public MarketDataSource(final int playSpeed) {
            m_playSpeed = playSpeed;
        }
        
        abstract public Path getFilePath(final LocalDate date);
        
        abstract public void playForDate(final LocalDate date, final Path filePath, final Optional<Long> startTimeNs) throws IOException, InterruptedException;

        protected void waitBeforePlayingTick(long timestamp, long playPreOpenStartTimeNs) throws InterruptedException {
            if (m_playSpeed > 0 && m_prevTimestamp != 0 && 
            		(m_prevTimestamp >= CT_START_TIME_1_NANO && m_prevTimestamp <= EI_START_TIME_NANO || 
            		m_prevTimestamp >= CT_START_TIME_2_NANO && m_prevTimestamp <= DC_START_TIME_NANO ||
            		m_prevTimestamp >= playPreOpenStartTimeNs && m_prevTimestamp <= EI_START_TIME_NANO)) {
                final long nanoToSleep = Math.max(timestamp - m_prevTimestamp, MIN_NANO_DELAY_BETWEEN_TICKS) / m_playSpeed;
                // Thread.sleep will sleep longer than 1000000 so use busy wait for any delay less than that
                if (nanoToSleep > 1000000) {
                    final long millis = nanoToSleep / 1000000L;
                    final int nanos = (int)(nanoToSleep - millis * 1000000L);
                    Thread.sleep(millis, nanos);
                }
                else if (nanoToSleep > 0) {
                    long start = System.nanoTime();
                    while (System.nanoTime() - start < nanoToSleep) {};
                }
            }
            m_prevTimestamp = Math.max(timestamp, m_prevTimestamp);
        }

        protected void updateMarketStatus(final long time) {
            if (marketStatus == MarketStatusType.DC) {
                if (time < CT_START_TIME_1_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.OI, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.OI;                    
                    sendMarketStatus();
                }
                else if (time < EI_START_TIME_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.CT, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.CT;
                    sendMarketStatus();                   
                }
                else if (time < CT_START_TIME_2_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.EI, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.EI;
                    sendMarketStatus();                   
                }
                else if (time < DC_START_TIME_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.CT, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.CT;
                    sendMarketStatus();                   
                }   
            }
            else if (marketStatus == MarketStatusType.OI) {
                if (time >= CT_START_TIME_1_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.CT, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.CT;
                    sendMarketStatus();
                }
            }
            else if (marketStatus == MarketStatusType.CT) {
                if (time >= EI_START_TIME_NANO && time < CT_START_TIME_2_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.EI, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.EI;
                    sendMarketStatus();
                }
                else if (time >= DC_START_TIME_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.DC, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.DC;
                    sendMarketStatus();
                }
            }
            else if (marketStatus == MarketStatusType.EI) {
                if (time >= CT_START_TIME_2_NANO) {
                    LOG.info("Update market status from {} to {} at {}...", marketStatus, MarketStatusType.CT, LocalTime.ofNanoOfDay(time));
                    marketStatus = MarketStatusType.CT;
                    sendMarketStatus();
                }
            }	
        }

        private void sendMarketStatus() {
            for (MarketStatusHandler handler : m_marketStatusHandlers) {
                handler.onMarketStatusChanged(ServiceConstant.PRIMARY_EXCHANGE, marketStatus);
            }
        }
    }

    static private volatile MarketDataReplayer s_instance;

    static public MarketDataReplayer instanceOf() {
        if (s_instance == null) {
            synchronized (MarketDataReplayer.class) {
                if (s_instance == null) {
                    s_instance = new MarketDataReplayer();
                }
            }
        }
        return s_instance;
    }

    private static int DEFAULT_HANDLER_CAPACITY = 10;	
    private final ArrayList<TradeHandler> m_tradeHandlers = new ArrayList<TradeHandler>(DEFAULT_HANDLER_CAPACITY);
    private final ArrayList<AggregateOrderBookUpdateHandler> m_aggregateOrderBookUpdateHandlers = new ArrayList<AggregateOrderBookUpdateHandler>(DEFAULT_HANDLER_CAPACITY);
    private final ArrayList<OrderBookSnapshotHandler> m_orderBookSnapshotHandlers = new ArrayList<OrderBookSnapshotHandler>(DEFAULT_HANDLER_CAPACITY);
    private final ArrayList<MarketStatusHandler> m_marketStatusHandlers = new ArrayList<MarketStatusHandler>(DEFAULT_HANDLER_CAPACITY);
    private final ExecutorService m_executor = Executors.newSingleThreadExecutor(new NamedThreadFactory("market-data", "market-data-replayer"));
    private char[] hsiFuturesCode;
    private long hsiFuturesSid;
    private char[] hsceiFuturesCode;
    private long hsceiFuturesSid;
    
    private MarketStatusType marketStatus = MarketStatusType.DC;

    public MarketStatusType getMarketStatus() {
        return marketStatus;
    }
    
    public void registerHsiFuturesCode(final long secSid, final String symbol) {
    	hsiFuturesSid = secSid;
    	hsiFuturesCode = symbol.toCharArray();
    }
    
    public void registerHsceiFuturesCode(final long secSid, final String symbol) {
    	hsceiFuturesSid = secSid;
    	hsceiFuturesCode = symbol.toCharArray();
    }

    public synchronized void addTradeHandler(final TradeHandler tradeHandler) {
        m_tradeHandlers.add(tradeHandler);
    }

    public synchronized void addAggreateOrderBookUpdateHandler(final AggregateOrderBookUpdateHandler aggreateOrderBookUpdateHandler) {
        m_aggregateOrderBookUpdateHandlers.add(aggreateOrderBookUpdateHandler);
    }
    
    public synchronized void addOrderBookSnapshotHandler(final OrderBookSnapshotHandler orderBookSnapshotHandler) {
    	m_orderBookSnapshotHandlers.add(orderBookSnapshotHandler);
    }

    public synchronized void addMarketStatusHandler(final MarketStatusHandler marketStatusHandler) {
        m_marketStatusHandlers.add(marketStatusHandler);
    }
    
    public void startFeed(final boolean useProcessedTicks, final int playSpeed, final Optional<Long> preOpenStartTimeNs, final SystemClock systemClock, final boolean waitToFinish) {
        startFeed(useProcessedTicks, playSpeed, preOpenStartTimeNs, systemClock, waitToFinish, null);
    };

    public void startFeed(final boolean useProcessedTicks, final int playSpeed, final Optional<Long> preOpenStartTimeNs, final SystemClock systemClock, final boolean waitToFinish, final Path filePath) {
        MarketDataSource marketDataSource = new ProcessedTicksReplayer(playSpeed);
        if (waitToFinish) {
            try {
                marketDataSource.playForDate(systemClock.date(), filePath == null ? marketDataSource.getFilePath(systemClock.date()) : filePath, preOpenStartTimeNs);
            } catch (IOException | InterruptedException e) {
                LOG.error("Exception occurred while replaying market data", e);
            }
        }
        else {			
            m_executor.execute(() -> {
                try {
                    marketDataSource.playForDate(systemClock.date(), filePath == null ? marketDataSource.getFilePath(systemClock.date()) : filePath, preOpenStartTimeNs);
                    m_executor.notify();
                } catch (IOException | InterruptedException e) {
                    LOG.error("Exception occurred while replaying market data", e);
                }
            });
        }
    };

    public void waitForFeedToFinish() throws InterruptedException {
        m_executor.awaitTermination(1, TimeUnit.DAYS);
    }

    private class ProcessedTicksReplayer extends MarketDataSource {
        //private static final String DATA_PATH = "D:/cygwin/data/omdc/";
    	private static final String DATA_PATH = "/data/omdc/";
        private static final String DATA_FILE = "ALL_ticks_%s_fast.csv";
        private final OmdcRow omdcRow = new OmdcRow();

        public ProcessedTicksReplayer(final int playSpeed) {
            super(playSpeed);
        }

        @Override
        public Path getFilePath(final LocalDate date) {
            return FileSystems.getDefault().getPath(DATA_PATH, String.format(DATA_FILE, date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))));
        }

        @Override
        public void playForDate(final LocalDate date, final Path path, final Optional<Long> playPreOpenStartTimeNs) throws IOException, InterruptedException {
            final Charset charset = Charset.forName("UTF-8");
            final BufferedReader reader = Files.newBufferedReader(path, charset);           
            
            boolean headerLine = true;
            LOG.info("Start replaying market data");
            
            char[] line = new char[1024];
            int c;
            int charIdx = 0;
            
            long playPreOpenStartTime = playPreOpenStartTimeNs.orElse(Long.MAX_VALUE);            
            while ((c = reader.read()) != -1) {
            	if ((char)c == '\r')
            		continue;
            	if ((char)c == '\n') {
                    if (headerLine) {
                        headerLine = false;
                        charIdx = 0;
                        continue;
                    }
                    
                    omdcRow.parseLine(line, charIdx);
                              
                    if (omdcRow.rowType == -1) {
                        charIdx = 0;
                    	continue;
                    }

                    final long scty = omdcRow.scty;

                    final long time = omdcRow.time;

                    if (time < OI_START_TIME_NANO) {
                        charIdx = 0;
                        continue;
                    }
                    updateMarketStatus(time);
                    
                    waitBeforePlayingTick(time, playPreOpenStartTime);

                    if (omdcRow.rowType == 1) {
                        final int price = omdcRow.price;
                        final int quantity = omdcRow.quantity;
                        for (TradeHandler tradeHandler : m_tradeHandlers) {
                            tradeHandler.onTradeMessageReceived(scty, time, price, quantity, 0);
                        }
                    }
                    else {
                        for (OrderBookSnapshotHandler orderSnapShotHandler : m_orderBookSnapshotHandlers) {
                            orderSnapShotHandler.onOrderUpdate(scty, time, omdcRow.numBids, omdcRow.bidPrices, omdcRow.bidQuantities, omdcRow.numAsks, omdcRow.askPrices, omdcRow.askQuantities);
                        }
                    }            		            		            		            	
            		
            		charIdx = 0;
            		continue;
            	}        	        	
            	line[charIdx] = (char)c;
            	charIdx++;
            }            
            
            LOG.info("Done replaying market data");
        }                
    }
    
    private class OmdcRow {
    	private final char[] charArray;
    	private long time;  //nanoOfDay
    	private long scty;
    	private int rowType; // 0 for market depth, 1 for trade
    	private int price;
    	private int quantity;
    	private int numBids;
    	private int numAsks;
    	private final int[] bidPrices;
    	private final int[] bidQuantities;
    	private final int[] askPrices;
    	private final int[] askQuantities;    	
    	
    	private static final int DEPTH = 5;
    	
	    private static final int MINUTES_PER_HOUR = 60;
	    private static final int SECONDS_PER_MINUTE = 60;
	    private static final long NANOS_PER_SECOND = 1000_000_000L;
	    private static final long NANOS_PER_MINUTE = NANOS_PER_SECOND * SECONDS_PER_MINUTE;
	    private static final long NANOS_PER_HOUR = NANOS_PER_MINUTE * MINUTES_PER_HOUR;
    	
    	public OmdcRow() {
    		charArray = new char[1024];
    		bidPrices = new int[DEPTH];
    		bidQuantities = new int[DEPTH];
    		askPrices = new int[DEPTH];
    		askQuantities = new int[DEPTH];
    	}
    	
    	public void parseLine(char[] line, int length) {    		
    		int charArrayIdx = 0;
    		int fieldNum = 0;
    		
    		boolean doneReadingLine = false;
    		rowType = -1;
    		numBids = 0;
    		numAsks = 0;
    		
    		for (int lineIdx = 0; lineIdx < length; lineIdx++) {
    			char c = line[lineIdx];
    			
    			if (c == ',') {
    				// Check if there are actually any characters between the last comma and the current comma
    				if (charArrayIdx != 0) {
    					switch (fieldNum) {
    					case 0:
    						time = charArrayToNanoOfDay(charArray);
    						break;    						
    					case 1:
    					    if (charArray[0] != 'H') {
    					        scty = charArrayToInt(charArray, 0, charArrayIdx);
    					    }
    					    else if (hsiFuturesSid != 0 && areEqual(hsiFuturesCode, charArray, 0, charArrayIdx)) {
    					    	scty = hsiFuturesSid;
    					    }
    					    else if (hsceiFuturesSid != 0 && areEqual(hsceiFuturesCode, charArray, 0, charArrayIdx)) {
    					    	scty = hsceiFuturesSid; 
    					    }
    					    else {
    					        scty = -1;
    					    }
    						break;
    					case 2:
    						if (charArray[0] == 'M')
    							rowType = 0;
    						else if (charArray[0] == 'T')
    							rowType = 1;    						
    						break;
    					case 4:
    						price = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 5:
    						quantity = charArrayToInt(charArray, 0, charArrayIdx);
    						if (rowType == 1) {        						    						
        						doneReadingLine = true;    							
    						}
    						break;
    					case 7:
    						numBids = 1;
    						bidPrices[0] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 8:
    						bidQuantities[0] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 9:
    						numBids = 2;
    						bidPrices[1] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 10:
    						bidQuantities[1] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;    		
    					case 11:
    						numBids = 3;
    						bidPrices[2] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 12:
    						bidQuantities[2] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;    		
    					case 13:
    						numBids = 4;
    						bidPrices[3] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 14:
    						bidQuantities[3] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;    		
    					case 15:
    						numBids = 5;
    						bidPrices[4] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 16:
    						bidQuantities[4] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 17:
    						numAsks = 1;
    						askPrices[0] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 18:
    						askQuantities[0] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 19:
    						numAsks = 2;
    						askPrices[1] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 20:
    						askQuantities[1] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;    						
    					case 21:
    						numAsks = 3;
    						askPrices[2] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 22:
    						askQuantities[2] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 23:
    						numAsks = 4;
    						askPrices[3] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 24:
    						askQuantities[3] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;    						
    					case 25:
    						numAsks = 5;
    						askPrices[4] = charArrayToInt(charArray, 0, charArrayIdx);
    						break;
    					case 26:
    						askQuantities[4] = charArrayToInt(charArray, 0, charArrayIdx);
    						doneReadingLine = true;
    						break;    						
    					default:
    					}    					    					    				
    				}
    				
    				if (doneReadingLine)
    					break;    					
    				
    				fieldNum++;
    				charArrayIdx = 0;
    				continue;
    			}   
    			
    			charArray[charArrayIdx] = c;			
    			charArrayIdx++;
    		}		    		    	
    	}
    	
    	private boolean areEqual(char[] ref, char[] data, int start, int end) {
    		if (ref.length == end - start) {
    			for (int i = start, j = 0; i < end; i++, j++) {
    				if (data[i] != ref[j]) {
    					return false;
    				}
    			}
    			return true;
    		}
    		return false;
    	}
    	
    	private int charArrayToInt(char[] data, int start, int end) throws NumberFormatException
    	{
    	    int result = 0;
    	    for (int i = start; i < end; i++)
    	    {
    	        int digit = (int)data[i] - (int)'0';
    	        if ((digit < 0) || (digit > 9)) 
    	            throw new NumberFormatException();
    	        result *= 10;
    	        result += digit;
    	    }
    	    
    	    return result;
    	}
    	
    	private long charArrayToNanoOfDay(char[] time) {
    		int hour = charArrayToInt(time, 11, 13);
    		int minute = charArrayToInt(time, 14, 16);
    		int second = charArrayToInt(time, 17, 19);
    		int nano = charArrayToInt(time, 20, 26) * 1000;    		                          			
    		
            long total = hour * NANOS_PER_HOUR;
            total += minute * NANOS_PER_MINUTE;
            total += second * NANOS_PER_SECOND;
            total += nano;    		
            
            return total;
    	}
    }

}
