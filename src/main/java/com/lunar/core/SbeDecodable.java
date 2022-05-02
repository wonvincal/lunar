package com.lunar.core;

import com.lunar.message.io.sbe.MessageDecoder;

public interface SbeDecodable<T extends MessageDecoder> {
    public void decodeFrom(final T decoder);
}
