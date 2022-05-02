package com.lunar.entity;

import java.util.Optional;

import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.util.Strings;

import com.lunar.message.binary.EntityEncoder;
import com.lunar.message.io.sbe.BooleanType;
import com.lunar.message.io.sbe.NoteSbeDecoder;
import com.lunar.message.io.sbe.NoteSbeEncoder;
import com.lunar.message.io.sbe.TemplateType;
import com.lunar.message.sender.NoteSender;
import com.lunar.service.ServiceConstant;

public class Note extends Entity {
	private long entitySid;
	private int createDate;
	private long createTime;
	private int updateDate;
	private long updateTime;
	private BooleanType isDeleted;
	private BooleanType isArchived;
	private String description;

	public static Note of(Optional<Long> sid, Optional<Long> entitySid, Optional<BooleanType> isDeleted, Optional<BooleanType> isArchived, Optional<String> description){
		if (!sid.isPresent() && !entitySid.isPresent()){
			throw new IllegalArgumentException("Cannot create Note without both note sid and entity sid");
		}
		return new Note(sid.orElse(ServiceConstant.NULL_NOTE_SID), 
				entitySid.orElse(ServiceConstant.NULL_ENTITY_SID),
				ServiceConstant.NULL_INT_DATE,
				ServiceConstant.NULL_TIME_NS,
				ServiceConstant.NULL_INT_DATE,
				ServiceConstant.NULL_TIME_NS,
				isDeleted.orElse(BooleanType.NULL_VAL),
				isArchived.orElse(BooleanType.NULL_VAL),
				description.orElse(Strings.EMPTY));
	}

	/**
	 * @param note
	 * @return
	 */
	public static Note of(NoteSbeDecoder note, MutableDirectBuffer stringBuffer){
//		String desc = note.description();
		int length = note.descriptionLength();
		note.getDescription(stringBuffer, 0, stringBuffer.capacity());
//		String desc = stringBuffer.getStringUtf8(0, note.descriptionLength());
//		System.out.println("Creating a Note with desc: " + desc);
		return new Note(note.noteSid(),
				note.entitySid(),
				note.createDate(),
				note.createTime(),
				note.updateDate(),
				note.updateTime(),
				note.isDeleted(),
				note.isArchived(),
				new String(stringBuffer.byteArray(), 0, length));
	}
	public static Note of(long sid, long entitySid, int createDate, long createTime, int updateDate, long updateTime, BooleanType isDeleted, BooleanType isArchived, String description){
		return new Note(sid, entitySid, createDate, createTime, updateDate, updateTime, isDeleted, isArchived, description);
	}
	
	Note(long sid, long entitySid, int createDate, long createTime, int updateDate, long updateTime, BooleanType isDeleted, BooleanType isArchived, String description) {
		super(sid);
		this.entitySid = entitySid;
		this.createDate = createDate;
		this.createTime = createTime;
		this.updateDate = updateDate;
		this.updateTime = updateTime;
		this.isArchived = isArchived;
		this.isDeleted = isDeleted;
		this.description = (description != null) ? description : Strings.EMPTY;
	}

	public long entitySid(){
		return entitySid;
	}
	
	public int createDate(){
		return createDate;
	}
	
	public Note createDate(int value){
		createDate = value;
		return this;
	}
	
	public long createTime(){
		return createTime;
	}
	
	public Note createTime(long value){
		createTime = value;
		return this;
	}

	public int updateDate(){
		return updateDate;
	}
	
	public Note updateDate(int value){
		updateDate = value;
		return this;
	}
	
	public long updateTime(){
		return updateTime;
	}
	
	public Note updateTime(long value){
		updateTime = value;
		return this;
	}

	public BooleanType isDeleted(){
		return isDeleted;
	}
	
	public Note isDeleted(BooleanType value){
		isDeleted = value; 
		return this;
	}
	
	public BooleanType isArchived(){
		return isArchived;
	}
	
	public Note isArchived(BooleanType value){
		isArchived = value; 
		return this;
	}
	
	public String description(){
		return description;
	}
	
	public Note description(String value){
		description = value;
		return this;
	}
	
	@Override
	public int encode(EntityEncoder encoder, MutableDirectBuffer buffer, int offset, MutableDirectBuffer stringBuffer) {
		return NoteSender.encodeNoteOnly(buffer, offset, encoder.noteSbeEncoder(), this);
	}

	@Override
	public TemplateType templateType() {
		return TemplateType.NOTE;
	}

	@Override
	public short blockLength() {
		return NoteSbeEncoder.BLOCK_LENGTH;
	}

	@Override
	public int expectedEncodedLength() {
		return NoteSbeEncoder.BLOCK_LENGTH + NoteSbeEncoder.descriptionHeaderLength() + description.length();
	}
	
	@Override
	public String toString() {
		return super.toString() + ", noteEntitySid: " + entitySid + ", desc: " + description + ", isArchived: " + isArchived.name() + ", isDeleted: " + isDeleted.name() + ", createDate: " + createDate + ", createTime: " + createTime + ", updateDate: " + updateDate + ", updateTime: " + updateTime;
	}

    @Override
    public int schemaId() {
        return NoteSbeEncoder.SCHEMA_ID;
    }

    @Override
    public int schemaVersion() {
        return NoteSbeEncoder.SCHEMA_VERSION;
    }
}
