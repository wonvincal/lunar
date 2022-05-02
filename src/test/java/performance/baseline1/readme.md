Encoding:       SBE
MessageCodec:   EchoSbe
Processor:      Disruptor

Latency Contributors:
+  Sender - Encode
1. Sender - Publish event to ring buffer (binary copy)
2. Receiver - Decode
3. Receiver - Encode
4. Receiver - Publish event to ring buffer (binary copy)
+  Sender - Decode 

Added
Include Pinger encoding and decoding time

Observation
Increase in max
Small difference in terms of 98%
