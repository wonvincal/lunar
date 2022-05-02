package com.lunar.entity;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.ExchangeSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sink.MessageSinkRef;

import org.agrona.MutableDirectBuffer;

public class Exchange extends Entity {
	public static final byte SEHK = 0;
	public static final String SEHK_CODE = "HKSE"; // because our db has it as HKSE
	public static final byte HKFE = 1;
	public static final String HKFE_CODE = "HKFE";
	
	public static final int INVALID_EXCHANGE_SID = -1;
	public static final String INVALID_EXCHANGE_NAME = "invalid";
	public static Exchange INVALID = new Exchange(INVALID_EXCHANGE_SID, INVALID_EXCHANGE_NAME, INVALID_EXCHANGE_NAME, null);

	// cold fields
	private final String name;
	private final String code;

	// hot fields
	private final MessageSinkRef mdsSink;

	public static Exchange of(long sid, String name, String code, MessageSinkRef mdsSink){
		return new Exchange(sid, name, code, mdsSink);
	}
	
	public Exchange(long sid, String name, String code, MessageSinkRef mdsSink){
		super(sid);
		this.name = name;
		this.code = code;
		this.mdsSink = mdsSink;
	}

	public String name(){
		return name;
	}
	
	public String code(){
		return code;
	}

	public MessageSinkRef mdsSink(){
		return mdsSink;
	}

    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TemplateType templateType() {
        return TemplateType.EXCHANGE;
    }

    @Override
    public short blockLength() {
        return ExchangeSbeEncoder.BLOCK_LENGTH;
    }

    @Override
    public int expectedEncodedLength() {
        return ExchangeSbeEncoder.BLOCK_LENGTH;
    }

    @Override
    public int schemaId() {
        return ExchangeSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return ExchangeSbeEncoder.SCHEMA_VERSION;
    }
    
    public static int getSidFromCode(final String code) {
    	switch (code) {
    	case SEHK_CODE:
    		return SEHK;
    	case HKFE_CODE:
    		return HKFE;
    	}
    	return INVALID_EXCHANGE_SID; 
    }
}
