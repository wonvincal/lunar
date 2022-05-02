Encoding:       SBE
MessageCodec:   EchoSbe
Processor:      Disruptor

Latency Contributors:
1. Sender - Encode
2. Sender - Publish event to ring buffer (binary copy)
+  Receiver - Pass to FSM onEvent method, then back to serviceOnEvent method
3. Receiver - Decode
4. Receiver - Encode
5. Receiver - Publish event to ring buffer (binary copy)
+  Sender - Pass to FSM onEvent method, then back to serviceOnEvent method
6. Sender - Decode 

Added
FSM base class

Observation
Increase in max
Small difference in terms of 98%
