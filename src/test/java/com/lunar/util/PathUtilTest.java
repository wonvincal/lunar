package com.lunar.util;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class PathUtilTest {

	@Test
	public void testCreateWritableFolder() throws IOException{
		String folderDesc = "test folder";
		String folder = "journal-test";
		Path path = PathUtil.createWritableFolderIfNotExist(folderDesc, folder);
		
		assertTrue(Files.exists(path));
		
		// Remove folder
		assertTrue(Files.deleteIfExists(path));
	}
}
