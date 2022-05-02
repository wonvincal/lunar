package com.lunar.marketdata.archive;

import org.agrona.concurrent.UnsafeBuffer;

public interface RawMessageWalker {
	int walkTo(UnsafeBuffer srcBuffer, int offset, int messageIndex);
	int walkAndRetrieveTo(UnsafeBuffer srcBuffer, int offset, int messageIndex, int numMsgToRetrieve, UnsafeBuffer dstBuffer);
	int getNextOffset(UnsafeBuffer srcBuffer, int offset);
	int getLength(UnsafeBuffer srcBuffer, int offset);
}
