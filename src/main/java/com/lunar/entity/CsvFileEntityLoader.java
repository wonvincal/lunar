package com.lunar.entity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class CsvFileEntityLoader<T extends Entity> implements EntityLoader<T> {
	private final Path path;
	private final EntityConverter<T> converter;
	
	public CsvFileEntityLoader(String uri, EntityConverter<T> converter){
		this.path = Paths.get(uri);
		this.converter = converter;
	}
	
	@Override
	public void loadInto(Loadable<T> loadable) throws Exception {
		try (Stream<String> lines = Files.lines(path)){
			lines.forEach(line -> loadable.load(converter.toEntity(line)));
		}		
	}
}
