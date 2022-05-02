Encoding:       SBE
MessageCodec:   EchoSbe
Processor:      Disruptor

Latency Contributors:
1. Sender - Publish event to ring buffer (binary copy)
2. Receiver - Decode
3. Receiver - Encode
4. Receiver - Publish event to ring buffer (binary copy)

November 11, 2015
=================
Use newly written MessageSender class.  Allow direct write to ring buffer.  
Use Intel(R) Core(TM) i5-5250U CPU @ 1.60GHz 