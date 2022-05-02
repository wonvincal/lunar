package org.agrona.concurrent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.HdrHistogram.Histogram;
import org.agrona.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class UnsafeBufferTest {
    private static final Logger LOG = LogManager.getLogger(UnsafeBufferTest.class);
    private static long HIGHEST_TRACKABLE_VALUE = 10000000000L;
    private static int NUM_SIGNIFICANT_DIGITS = 4;
    private static int EXPECTED_INTERNAL_IN_NS = 50_000_000;

    @Test
    public void testCreate() throws UnsupportedEncodingException{
        ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(outputByteStream);

        LOG.debug("Native byte order:{}", BufferUtil.NATIVE_BYTE_ORDER.toString());
        int size = 1024;
        int sizeOfInt = 4;
        int numLoop = 1000000;
        double scale = (double)size / sizeOfInt;
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(size));
        
        Histogram histogram = new Histogram(HIGHEST_TRACKABLE_VALUE, NUM_SIGNIFICANT_DIGITS);
        
        for (int loop = 0; loop < numLoop; loop++){
            long startTimeSystemNs = System.nanoTime();
            for (int i = 0; i < size; i += sizeOfInt){
                buffer.putInt(i, i, ByteOrder.LITTLE_ENDIAN);
            }
            histogram.recordValueWithExpectedInterval(System.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
        }
        histogram.outputPercentileDistribution(outputStream, 1, scale);
        
        for (int i = 0; i < size; i += sizeOfInt){
            LOG.info("buffer[{}] = {}", i, buffer.getInt(i));
            buffer.putInt(i, i, ByteOrder.LITTLE_ENDIAN);
        }
        LOG.info("Before Output\n{}", outputByteStream.toString("UTF8"));
        outputByteStream.reset();
        histogram.reset();
        for (int loop = 0; loop < numLoop; loop++){
            long startTimeSystemNs = System.nanoTime();
            for (int i = 0; i < size; i += sizeOfInt){
                buffer.putInt(i, i, ByteOrder.LITTLE_ENDIAN);
            }
            histogram.recordValueWithExpectedInterval(System.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
        }
        histogram.outputPercentileDistribution(outputStream, 1, 256.0);
        LOG.info("buffer[{}] = {}", 0, buffer.getInt(0));
        LOG.info("buffer[{}] = {}", 1, buffer.getInt(4));
        LOG.info("buffer[{}] = {}", 2, buffer.getInt(8));
        LOG.info("After Output\n{}", outputByteStream.toString("UTF8"));
    }
    
    @Test
    public void testCreateWithNoBoundCheck() throws UnsupportedEncodingException{
        ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(outputByteStream);

        LOG.debug("Native byte order:{}", BufferUtil.NATIVE_BYTE_ORDER.toString());
        int size = 1024;
        int sizeOfInt = 4;
        int numLoop = 1000000;
        double scale = (double)size / sizeOfInt;
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(size));
        
        Histogram histogram = new Histogram(HIGHEST_TRACKABLE_VALUE, NUM_SIGNIFICANT_DIGITS);
        
        for (int loop = 0; loop < numLoop; loop++){
            long startTimeSystemNs = System.nanoTime();
            for (int i = 0; i < size; i += sizeOfInt){
                buffer.putInt(i, i, ByteOrder.LITTLE_ENDIAN);
            }
            histogram.recordValueWithExpectedInterval(System.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
        }
        histogram.outputPercentileDistribution(outputStream, 1, scale);
        
        for (int i = 0; i < size; i += sizeOfInt){
            LOG.info("buffer[{}] = {}", i, buffer.getInt(i));
        }
        LOG.info("Before Output\n{}", outputByteStream.toString("UTF8"));
        outputByteStream.reset();
        histogram.reset();
        for (int loop = 0; loop < numLoop; loop++){
            long startTimeSystemNs = System.nanoTime();
            for (int i = 0; i < size; i += sizeOfInt){
                buffer.putInt(i, i, ByteOrder.LITTLE_ENDIAN);
            }
            histogram.recordValueWithExpectedInterval(System.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
        }
        histogram.outputPercentileDistribution(outputStream, 1, scale);
        LOG.info("After Output\n{}", outputByteStream.toString("UTF8"));
    }
    
    @Test
    public void testCreateWithWrongByteOrder() throws UnsupportedEncodingException{
        ByteArrayOutputStream outputByteStream = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(outputByteStream);

        LOG.debug("Native byte order:{}", BufferUtil.NATIVE_BYTE_ORDER.toString());
        ByteOrder targetByteOrder = ByteOrder.LITTLE_ENDIAN;
        if (BufferUtil.NATIVE_BYTE_ORDER == ByteOrder.LITTLE_ENDIAN){
            targetByteOrder = ByteOrder.BIG_ENDIAN;
        }
        int size = 1024;
        int sizeOfInt = 4;
        int numLoop = 1000000;
        double scale = (double)size / sizeOfInt;
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(size));
        
        Histogram histogram = new Histogram(HIGHEST_TRACKABLE_VALUE, NUM_SIGNIFICANT_DIGITS);
        
        for (int loop = 0; loop < numLoop; loop++){
            long startTimeSystemNs = System.nanoTime();
            for (int i = 0; i < size; i += sizeOfInt){
                buffer.putInt(i, i, targetByteOrder);
            }
            histogram.recordValueWithExpectedInterval(System.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
        }
        histogram.outputPercentileDistribution(outputStream, 1, scale);
        
        for (int i = 0; i < size; i += sizeOfInt){
            LOG.info("buffer[{}] = {}", i, buffer.getInt(i));
        }
        LOG.info("Before Output\n{}", outputByteStream.toString("UTF8"));
        outputByteStream.reset();
        histogram.reset();
        for (int loop = 0; loop < numLoop; loop++){
            long startTimeSystemNs = System.nanoTime();
            for (int i = 0; i < size; i += sizeOfInt){
                buffer.putInt(i, i, targetByteOrder);
            }
            histogram.recordValueWithExpectedInterval(System.nanoTime() - startTimeSystemNs, EXPECTED_INTERNAL_IN_NS);
        }
        histogram.outputPercentileDistribution(outputStream, 1, scale);
        LOG.info("After Output\n{}", outputByteStream.toString("UTF8"));
    }
}
