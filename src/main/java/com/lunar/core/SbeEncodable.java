package com.lunar.core;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.TemplateType;

import org.agrona.MutableDirectBuffer;

public interface SbeEncodable {
	int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer);
	TemplateType templateType();
	short blockLength();
	int expectedEncodedLength();
	int schemaId();
	int schemaVersion();
}
