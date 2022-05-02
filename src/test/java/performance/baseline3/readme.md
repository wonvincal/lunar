Encoding:       SBE
MessageCodec:   EchoSbe
Processor:      Disruptor

Latency Contributors:
1. Sender - Encode
2. Sender - Publish event to ring buffer (binary copy)
3  Receiver - Pass to FSM onEvent method, then back to serviceOnEvent method
4. Receiver - Decode
5. Receiver - Encode
6. Receiver - Publish event to ring buffer (binary copy)
7. Sender - Pass to FSM onEvent method, then back to serviceOnEvent method
8. Sender - Decode 

Added
Message context class - using array index to map to a message sink

Observation
Increase in max
Small difference in terms of 98%
