package com.lunar.entity;

import java.util.Collection;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.StrategyParamSource;
import com.lunar.message.io.sbe.StrategyParamType;
import com.lunar.message.io.sbe.StrategyTypeSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.StrategySender;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.agrona.MutableDirectBuffer;

public class StrategyType extends Entity {
    public class Field {
        private int parameterId;
        private StrategyParamSource source;
        private StrategyParamType type;
        private BooleanType isReadOnly;
        
        Field(final int parameterId, final StrategyParamSource source, final StrategyParamType type, final BooleanType isReadOnly) {
            this.parameterId = parameterId;
            this.source = source;
            this.type = type;
            this.isReadOnly = isReadOnly;
        }
        
        public int parameterId() {
        	return parameterId;
        }
        
        public StrategyParamSource source() {
        	return source;
        }
        
        public StrategyParamType type() {
        	return type;
        }
        
        public BooleanType isReadOnly() {
        	return isReadOnly;
        }
    }

    static int MAX_FIELDS = 10;
    private String name;

    private ObjectArrayList<Field> fields;    
    
    static public StrategyType of(final long sid, final String name) {
        return new StrategyType(sid, name);
    }
    
    protected StrategyType(final long sid, final String name) {
        super(sid);
        this.name = name;
        this.fields = ObjectArrayList.wrap(new Field[MAX_FIELDS]);
        this.fields.size(0);
    }
    
    public StrategyType name(final String name) {
        this.name = name;
        return this;
    }
    
    public String name() {
        return this.name;
    }
    
    public Collection<Field> getFields() {
        return this.fields;
    }
    
    public StrategyType addField(final int parameterId, final StrategyParamSource source, final StrategyParamType type, final BooleanType isReadOnly) {
        this.fields.add(new Field(parameterId, source, type, isReadOnly));
        return this;
    }
    
    @Override
    public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
        return StrategySender.encodeStrategyTypeWithoutHeader(buffer, offset, stringBuffer, encoder.strategyTypeSbeEncoder(), this);
    }
    @Override
    public TemplateType templateType(){
        return TemplateType.STRATEGYTYPE;
    }

    @Override
    public short blockLength() {
        return StrategyTypeSbeEncoder.BLOCK_LENGTH;
    }
    
    @Override
    public int expectedEncodedLength() {
        return StrategyTypeSbeEncoder.BLOCK_LENGTH;
    }

    @Override
    public int schemaId() {
        return StrategyTypeSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return StrategyTypeSbeEncoder.SCHEMA_VERSION;
    }

}
